# Loot authoring module

- Server: `loot`; client drafts/layout: `loot.client`. Native loot JSON remains authoritative. One item `hostile_humans_unified:loot_editor`, discovered by the existing creative tab.
- Active table IDs come from `LootDataManager`, source JSON from the selected resource pack; runtime-only tables use native serializers. Source data is not a promise that Forge GLMs or load hooks leave final rewards unchanged.
- Required author datapack `hostile_humans_unified:author_loot` exposes only `hhu_author` JSON from instance config. Official HHU tables are mirrored for reference but never loaded back from that mirror. Explicit publish saves then reloads; no per-edit reload. Native deserialization and ValidationContext validate before writes; invalid contexts cannot be bound to containers.
- JSON byte/depth/node limits; bounded pages and packets; author permission, held tool, connection-scoped token and dimension checked on every operation. No idle timeout. File revision conflicts and target-container fingerprints prevent stale destructive writes. Binding requires loaded chunks and distance <=8; selecting does not unpack loot.
- Native RandomizableContainerBlockEntity is the sole current adapter. Double chest halves are checked/updated together and rolled independently, seed0; occupied inventories require explicit replacement. Custom storage types fail closed. Preserve coordinates and all other BE fields. Existing template capture saves the native tags unchanged.
- Publishing resources is separate from generating inventory: no random rewards are generated merely by reading/editing JSON. Export keeps native data paths and references; dependencies must be included separately. Export is server-side, not a download of server files to clients.
- UI: library, common pool/item editing, advanced lossless tree. Fixed action bar, group headers, item-name search, simple pool chips in rows of four, explicit update-draft buttons, single Save/apply action. Complex count providers use advanced editing. Copy keeps original JSON including unknown fields/functions. Close or read another document requires confirmation when dirty.

## Acceptance

Java pure tests cover JSON limits, escaped delimiters, draft isolation and opaque function/condition preservation. Native integration checks must exercise dedicated server boot, a loaded third-party chest table, native-context rejection, publish/reload failure, external version conflict, container/hopper change, double chest rollback and template save/place/open. UI acceptance includes small window, Chinese names, scrolling, input focus, raw fragments, existing-items confirmation and dirty exit. Build evidence belongs in WORKSTREAMS/release records; live acceptance remains separate.

## Localized item selection and JEI

- LootItemCatalog caches current-language names and native en_us fallback resources separately, invalidated on resource reload or language change. Search matches localized/English names, IDs and @namespace/mod with whitespace-AND matching; results are debounced and paginated. Catalog never changes saved IDs.
- PanelItemRow uses native item rendering and common control styling. The ordinary picker replaces raw-ID entry; manual ID editing stays available. Icons may apply unconditional static set_nbt to a representative copy, but never execute random loot functions or claim to simulate final rewards.
- compat.jei is discovered only by JEI's public @JeiPlugin. addGuiScreenHandler publishes the current editor rectangle, addGhostIngredientHandler accepts only ItemStack type references. Only visible/clipped picker areas receive drops; pending requests and non-item pages refuse. No inventory transfer or immediate server publication; explicit selected stack NBT is represented by the native set_nbt function. JEI 15.25+ quickMove is also supported; the main typed ghost API exists since JEI12.2.
- A <=232px total side gutter, split 2:3 between bookmarks and item list, and 26px bottom gutter reserve compact native JEI space while keeping >=400px of logical width available before ordinary frame margins. Very narrow windows reduce gutter size. No global JEI configuration changes.
- compileOnly -PjeiApiJar is isolated from runtime. Maintenance uses a read-only cached API input so removing JEI from the target instance does not prevent compilation; absent-dependency linkage excludes only compat.jei and asserts JEI is not in runtime classpath. No copies of JEI enter the mod artifact.

- Exact JEI/offhand examples retain their selected NBT (<=16KiB) through LootSimpleDraft.withStackData and vanilla set_nbt. Reuse a final unconditional function, preserve other conditions/functions, omit redundant empty functions, and apply only on explicit selected-item commits. Counts remain the author's configured range. Native registry search enumerates item types; a dependency-free Supplier interface merges up to32000 JEI ItemStack examples, deduplicating IDs+NBT, replacing unconfigured base examples when JEI supplies valid variants. Runtime availability/unavailability invalidates this cache; the UI can search their localized/English names. Additional variants can still be dragged or selected from held examples.

## 编辑器内帮助

LootEditorScreen的只读帮助页复用PanelScreen布局、PanelText自动换行和原生滚动，六类主题一次只显示一类；内容由zh_cn/en_us的loot_editor.guide.*提供。进入／返回帮助不重置draft、path或InputValue；帮助页不创建发布／绑定按钮，也无JEI投放目标。高级树节点额外区分池和候选并保留原始字段名。


## Draft continuity

Client draft cache is world/server scoped under gameDirectory/hostile_humans_unified/loot-drafts, atomically replaced once per second when changed and on screen removal. It retains the JSON document, selected pool, pending fields and picked stack data, but never session tokens or permissions. Recovered drafts require authoritative publication/load before binding. Two clicks within80ticks clear the local draft only. JEI child screens retain the live parent and route outstanding responses to it; highest-priority Escape returns without abandoning the author session.
