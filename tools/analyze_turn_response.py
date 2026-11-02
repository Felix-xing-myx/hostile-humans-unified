"""Reference mathematics for TurnGesture/TurnResponse; never compiles or executes Java."""
from pathlib import Path
from dataclasses import dataclass
import argparse,math,json,csv,re,hashlib,statistics
ROOT=Path(__file__).resolve().parents[1]
MASK=(1<<64)-1

def wrap(x):return (x%360+540)%360-180
def clip(x,a,b):return max(a,min(b,x))
def mix(x):
 x&=MASK;x=((x^(x>>30))*0xbf58476d1ce4e5b9)&MASK;x=((x^(x>>27))*0x94d049bb133111eb)&MASK
 return x^(x>>31)
def unit(x):return (mix(x)>>11)*2**-53
def normal(x):return math.sqrt(-2*math.log(max(2**-53,unit(x))))*math.cos(2*math.pi*unit(x+31))
def smooth(x):return x*x*x*(10+x*(-15+6*x))
def deriv(x):return 30*x*x*(1-x)*(1-x)
def second(x):return 60*x*(1-x)*(1-2*x)
def third(x):return 60*(1-6*x+6*x*x)
@dataclass(frozen=True)
class Axis:
 angle:float=0;velocity:float=0;acceleration:float=0;target:float=math.nan;tracking:float=0
@dataclass(frozen=True)
class Style:
 tempo:float;skew:float;landing:float;response:float;braking:float;jerk:float
@dataclass(frozen=True)
class Segment:
 angle:float;velocity:float;acceleration:float;goal:float;duration:float;skew:float;landing:float
 def at(self,t,goal,tracking):
  drift=wrap(goal-self.goal);d=self.duration
  if t>=d:
   c=max(2,d*.45)
   if t>=d+c:return goal,tracking,0,0
   u=clip((t-d)/c,0,1);r=wrap(self.goal-self.angle)*self.landing
   return goal+r*(1-smooth(u)),tracking-r*deriv(u)/c,-r*second(u)/(c*c),-r*third(u)/(c*c*c)
  u=clip(t/d,0,1);g=u*(1-u);w=u+16*self.skew*g**3
  dw=1+48*self.skew*g*g*(1-2*u);ddw=96*self.skew*g*(1-5*u+5*u*u);dddw=96*self.skew*(1-12*u+30*u*u-20*u*u*u)
  distance=wrap(self.goal-self.angle)*(1+self.landing);v=self.velocity*d;a=self.acceleration*d*d
  c3=10*distance-6*v-1.5*a;c4=-15*distance+8*v+1.5*a;c5=6*distance-3*v-.5*a
  q=self.angle+v*w+.5*a*w*w+c3*w**3+c4*w**4+c5*w**5
  dq=v+a*w+3*c3*w*w+4*c4*w**3+5*c5*w**4;ddq=a+6*c3*w+12*c4*w*w+20*c5*w**3
  dddq=6*c3+24*c4*w+60*c5*w*w
  b=smooth(u);db=deriv(u)/d;ddb=second(u)/(d*d)
  return q+b*drift,dq*dw/d+db*drift+b*tracking,(ddq*dw*dw+dq*ddw)/(d*d)+ddb*drift+2*db*tracking,(dddq*dw**3+3*ddq*dw*ddw+dq*dddw)/(d**3)+third(u)*drift/d**3+3*ddb*tracking

def style(seed,n,amplitude,urgent):
 k=mix(seed+n*0x9e3779b97f4a7c15)
 tempo=clip((.8+(.045 if urgent else .1)*normal(k))*(.95+.1*unit(seed)),.72 if urgent else .65,.95 if urgent else 1.1)
 skew=(unit(k+1)*2-1)*(.15 if urgent else .3);landing=0
 if not urgent and amplitude>=10 and unit(k+2)<.30:
  bound=min(1.5,amplitude*.02);landing=clip(-.15+.6*normal(k+3),-bound,bound)/amplitude
 return Style(tempo,skew,landing,.9+.2*unit(k+4),.85+.3*unit(k+5),.85+.3*unit(k+6))

def advance(old,goal,segment,elapsed,s,p,speed,reset=False,body=0,neck=180):
 delta=wrap(goal-old.target);tracking=.5*old.tracking+.5*delta if not reset and abs(delta)<=max(12,p[0]*.5) else 0
 angle,v,a=old.angle,old.velocity,old.acceleration;dt=.25
 speed=max(.01,min(p[0],speed));ceiling=max(speed,abs(v))
 for i in range(4):
  local=(i+1)*dt;sampled=goal+tracking*(local-1)
  r,rv,ra,rj=segment.at(elapsed+local,sampled,tracking) if segment else (sampled,tracking,0,0)
  rv=clip(rv,-speed,speed);available=neck-abs(wrap(angle-body))
  if neck<180:
   if abs(wrap(r-body))>neck:rv=ra=rj=0
   r=body+clip(wrap(r-body),-neck,neck)
   if wrap(angle-body)*rv>0:rv=math.copysign(min(abs(rv),math.sqrt(2*p[1]*max(0,available-2))),rv)
  gain=p[2]*s.response;request=rj+gain**3*wrap(r-angle)+3*gain*gain*(rv-v)+3*gain*(ra-a)
  bound=p[1]*(s.braking if request*v<0 else 1)
  if abs(v)>speed+1:bound=max(bound,min(16,abs(v)*.65))
  jerk=max(p[3]*s.jerk,bound*.6 if abs(v)>speed+1 else 0)
  if a*v>0 and abs(a)>math.sqrt(2*jerk*max(0,speed-abs(v))):request=-math.copysign(jerk,a)
  a=clip(a+clip(request,-jerk,jerk)*dt,-max(bound,abs(old.acceleration)),max(bound,abs(old.acceleration)))
  nv=clip(v+a*dt,-ceiling,ceiling)
  angle=wrap(angle+(v+nv)*.5*dt);v=nv
 if abs(wrap(goal-angle))<.02 and abs(v)<.04 and abs(a)<.08 and abs(tracking)<.02:return Axis(wrap(goal),0,0,goal,0)
 return Axis(angle,v,a,goal,tracking)

class Frame:
 def __init__(self,seed=0):self.seed=seed;self.tick=None;self.current=None;self.start=None
 def step(self,now,actual,goals,p,speed,intent=0,limit=75):
  if self.tick!=now or self.start is None:
   if self.current is None or now!=self.tick+1:self.current=tuple(Axis(x) for x in actual)+(None,0,intent)
   self.start=self.current;self.tick=now
  b,h,pitch,g,n,oldintent=self.start;wb,wh,wp=goals;wp=clip(wp,-89,89)
  amp=max(abs(wrap(wb-b.angle)),math.hypot(wrap(wh-h.angle),wp-pitch.angle));changed=intent!=oldintent
  if changed:g=None
  if g and now-g[0]>g[2][0].duration*1.45+2 and amp<.08 and abs(h.velocity)+abs(b.velocity)+abs(pitch.velocity)<.12:g=None
  urgent_redirect=g and p==PROFILES['URGENT'] and (not g[3] or abs(wrap(wh-h.target))>20)
  replan=urgent_redirect or g and now-g[0]>=6 and amp>35 and abs(wrap(wh-g[2][1].goal))>20
  if (g is None and amp>=8) or (changed and amp>3) or replan:
   n+=1;s=style(self.seed,n,amp,p==PROFILES['URGENT'])
   base=1.5+.58*math.sqrt(amp) if p==PROFILES['URGENT'] else 2+.75*math.sqrt(amp)
   planning=1.25 if p==PROFILES['URGENT'] else 1.50 if p==PROFILES['COMBAT'] else 1
   duration=max(3,base*s.tempo*planning)
   if p!=PROFILES['IDLE']:
    for _ in range(2):
     ratio=1
     for x,y,factor,bodyfactor in zip((b,h,pitch),(wb,wh,wp),((1 if p==PROFILES['URGENT'] else 1.08),1,1),((1 if p==PROFILES['URGENT'] else .8),1,1)):
      seg=Segment(x.angle,x.velocity,x.acceleration,y,duration*factor,s.skew,s.landing)
      for sample in range(13):
       _,v,a,j=seg.at(seg.duration*sample/12,seg.goal,0)
       bound=max(p[1]*bodyfactor*min(1,s.braking),abs(seg.acceleration))
       ratio=max(ratio,abs(v)/(max(.01,min((speed*.9 if bodyfactor<1 else min(speed,12) if x is pitch else speed),p[0]*(.9 if bodyfactor<1 else 1)))),math.sqrt(abs(a)/bound),(abs(j)/(p[3]*bodyfactor*s.jerk))**(1/3))
     if ratio<=1:break
     duration*=min(1.6,ratio)*1.02
   ds=[duration*(1 if p==PROFILES['URGENT'] else 1.08),duration,duration]
   segs=[Segment(x.angle,x.velocity,x.acceleration,y,d,s.skew,s.landing) for x,y,d in zip((b,h,pitch),(wb,wh,wp),ds)]
   g=(now,s,segs,p==PROFILES['URGENT'])
  s=g[1] if g else Style(1,0,0,1,1,1);elapsed=now-g[0] if g else 0;segs=g[2] if g else [None]*3
  bp=PROFILES.get('IDLE_BODY') if p==PROFILES['IDLE'] else PROFILES.get('COMBAT_BODY') if p==PROFILES['COMBAT'] else p
  b=advance(b,wb,segs[0],elapsed,s,bp,speed if bp==p else speed*.9,changed)
  h=advance(h,wh,segs[1],elapsed,s,p,speed,changed,b.angle,max(0,limit))
  pitch=advance(pitch,wp,segs[2],elapsed,s,p,min(speed,12),changed)
  if abs(pitch.angle)>89:pitch=Axis(math.copysign(89,pitch.angle),0,0,pitch.target,pitch.tracking)
  rel=wrap(h.angle-b.angle)
  if abs(rel)>limit:h=Axis(wrap(b.angle+clip(rel,-limit,limit)),b.velocity,b.acceleration,h.target,h.tracking)
  self.current=(b,h,pitch,g,n,intent);return b.angle,h.angle,pitch.angle

 def stop(self,now,actual,limit=75):
  if self.tick!=now or self.start is None:
   if self.current is None or now!=self.tick+1:self.current=tuple(Axis(x) for x in actual)+(None,0,0)
   self.start=self.current;self.tick=now
  def brake(old):
   angle,v,a=old.angle,old.velocity,old.acceleration;dt=.25
   for _ in range(4):
    j=clip(-4*v-4*a,-30,30);bound=max(16,abs(old.acceleration));a=clip(a+j*dt,-bound,bound);nv=v+a*dt
    if v*nv<0:angle=wrap(angle+v*.5*dt);v=a=0;break
    angle=wrap(angle+(v+nv)*.5*dt);v=nv
   if abs(v)<.02 and abs(a)<.04:v=a=0
   return Axis(angle,v,a)
  b,h,p=map(brake,self.start[:3]);relative=wrap(h.angle-b.angle)
  if abs(relative)>limit:h=Axis(wrap(b.angle+clip(relative,-limit,limit)),b.velocity,b.acceleration)
  if abs(p.angle)>89:p=Axis(math.copysign(89,p.angle))
  self.current=(b,h,p,None,self.start[4],0);return b.angle,h.angle,p.angle

SOURCE=ROOT/'src/main/java/dev/felix/hostilehumans/perception/TurnResponse.java'
PROFILES={k:tuple(map(float,v.split(','))) for k,v in re.findall(r'\b(IDLE(?:_BODY)?|COMBAT(?:_BODY)?|URGENT)=new Profile\(([^)]+)\)',SOURCE.read_text(encoding='utf-8'))}

def audit(output):
 traces=[];stats={};checks=0
 for name in ['COMBAT','URGENT','IDLE']:
  p=PROFILES[name];times=[];shapes=set();endpoint_errors=[];normalized=set();overshoots=[]
  for seed in range(128):
   f=Frame(seed);pose=(0,0,0);first=None;path=[];errors=[]
   for t in range(160):
    pose=f.step(t,pose,(90,90,0),p,1.5 if name=='IDLE' else p[0]);checks+=1
    assert all(math.isfinite(x) for x in pose)
    assert abs(wrap(pose[1]-pose[0]))<=75.00001
    errors.append(max(abs(wrap(90-pose[0])),abs(wrap(90-pose[1]))));path.append(pose[0])
    if first is None and errors[-1]<=5:first=t+1
    if seed<6:traces.append((name,seed,t,*pose))
   assert first is not None,(name,seed,pose)
   assert max(abs(wrap(90-pose[0])),abs(wrap(90-pose[1])))<.1,(name,seed,pose)
   last_outside=max([i for i,e in enumerate(errors) if e>5]+[-1]);stable=last_outside+2
   times.append(stable);endpoint_errors.append(abs(wrap(90-pose[0])))
   overshoots.append(max(0,max(path)-90))
   normalized.add(tuple(round(path[min(len(path)-1,round((stable-1)*fraction))]/90,3) for fraction in (.25,.5,.75)))
   s=style(seed,1,90,name=='URGENT');shapes.add(round(s.skew,5))
  stats[name]={'mean_5deg_ticks':statistics.mean(times),'min_ticks':min(times),'max_ticks':max(times),'distinct_skews':len(shapes),'distinct_normalized_traces':len(normalized),'maximum_overshoot_degrees':max(overshoots)}
 for seed in range(40):
  f=Frame(seed);pose=(0,0,0)
  for t in range(240):
   target=wrap(t*4) if t<80 else 0 if t<160 else 179 if t%40<20 else -179
   old=f.step(t,pose,(target,target,0),PROFILES['COMBAT'],26)
   repeated=f.step(t,(120,120,0),(target,target,0),PROFILES['COMBAT'],26)
   assert old==repeated;checks+=1;pose=old
  before=f.step(300,(45,45,0),(45,45,0),PROFILES['COMBAT'],26);assert before==(45,45,0);checks+=1
 # One entity must vary between complete actions, not just differ from other entities.
 f=Frame(42);pose=(0,0,0);styles=[]
 for episode in range(12):
  goal=45 if episode%2==0 else -45
  for offset in range(100):
   pose=f.step(episode*100+offset,pose,(goal,goal,0),PROFILES['COMBAT'],30,episode+1)
   if offset==0:styles.append(f.current[3][1].skew)
 assert len(set(styles))==12;checks+=1
 # Do not sacrifice the existing 5-degree firing gate to improve appearance.
 tracking_results={}
 for name,velocity in [('COMBAT',4),('COMBAT',8),('URGENT',12),('URGENT',18)]:
  f=Frame(14);pose=(0,0,0);errors=[]
  for t in range(180):
   goal=wrap(t*velocity);pose=f.step(t,pose,(goal,goal,0),PROFILES[name],PROFILES[name][0],1)
   if t>=60:errors.append(max(abs(wrap(goal-x)) for x in pose[:2]))
  assert max(errors)<5;checks+=1
  tracking_results[name+'_'+str(velocity)]={'maximum_error_degrees':max(errors)}
 stats['tracking']=tracking_results
 for start in (1,3,5,8):
  f=Frame(42);pose=(0,0,0)
  for t in range(start):pose=f.step(t,pose,(90,90,0),PROFILES['COMBAT'],30,1)
  history=[]
  for t in range(start,start+70):
   pose=f.stop(t,pose);history.append(pose)
   assert pose==f.stop(t,(100,100,0));checks+=1
  assert max(abs(wrap(history[-1][i]-history[-10][i])) for i in range(3))<.001
  checks+=1
 for skew in [-.3,0,.3]:
  seg=Segment(10,3,-.5,90,10,skew,.01);q,v,a,j=seg.at(0,90,0)
  assert abs(q-10)<1e-9 and abs(v-3)<1e-9 and abs(a+.5)<1e-9
  checks+=1
 stats['assertions']=checks;stats['scope']='reference mathematics plus source-derived parameters; not Java/game execution'
 stats['source_hashes']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [SOURCE,SOURCE.with_name('TurnGesture.java')]}
 output.mkdir(parents=True,exist_ok=True);(output/'reference.json').write_text(json.dumps(stats,indent=2),encoding='utf-8')
 with (output/'traces.csv').open('w',encoding='utf-8',newline='') as out:
  w=csv.writer(out);w.writerow(['mode','seed','tick','body','head','pitch']);w.writerows(traces)
 print(json.dumps(stats))
if __name__=='__main__':
 a=argparse.ArgumentParser();a.add_argument('--output',type=Path,required=True);audit(a.parse_args().output)
