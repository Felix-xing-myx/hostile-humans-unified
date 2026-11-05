package dev.felix.hostilehumans.compat.jei;

import dev.felix.hostilehumans.loot.client.LootEditorScreen;
import dev.felix.hostilehumans.loot.client.LootItemCatalog;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.constants.VanillaTypes;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** JEI alone discovers this optional adapter. Core UI has no JEI types or runtime singleton. */
@JeiPlugin
public final class LootAuthoringJeiPlugin implements IModPlugin {
    @Override public ResourceLocation getPluginUid(){return new ResourceLocation("hostile_humans_unified","loot_authoring");}
    @Override public void registerGuiHandlers(IGuiHandlerRegistration registration){
        registration.addGuiScreenHandler(LootEditorScreen.class,screen->{
            var bounds=screen.editorBounds();
            return new IGuiProperties(){
                @Override public Class<? extends Screen> getScreenClass(){return LootEditorScreen.class;}
                @Override public int getGuiLeft(){return bounds.x();}
                @Override public int getGuiTop(){return bounds.y();}
                @Override public int getGuiXSize(){return bounds.width();}
                @Override public int getGuiYSize(){return bounds.height();}
                @Override public int getScreenWidth(){return screen.width;}
                @Override public int getScreenHeight(){return screen.height;}
            };
        });
        registration.addGhostIngredientHandler(LootEditorScreen.class,new GhostItems());
    }
    @Override public void onRuntimeAvailable(IJeiRuntime runtime){
        LootItemCatalog.variants(()->runtime.getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK));
    }
    @Override public void onRuntimeUnavailable(){LootItemCatalog.variants(List::of);}
    private static final class GhostItems implements IGhostIngredientHandler<LootEditorScreen> {
        @Override public <I> List<Target<I>> getTargetsTyped(LootEditorScreen screen,ITypedIngredient<I> ingredient,boolean start){
            if(!(ingredient.getIngredient() instanceof ItemStack stack)||stack.isEmpty())return List.of();
            return screen.itemDropAreas().stream().<Target<I>>map(bounds->new Target<I>(){
                @Override public Rect2i getArea(){return new Rect2i(bounds.x(),bounds.y(),bounds.width(),bounds.height());}
                @Override public void accept(I value){if(value instanceof ItemStack selected)screen.acceptGhostItem(selected);}
            }).toList();
        }
        @Override public <I> boolean quickMove(LootEditorScreen screen,ITypedIngredient<I> ingredient){
            return ingredient.getIngredient() instanceof ItemStack stack&&screen.acceptGhostItem(stack);
        }
        @Override public void onComplete(){}
    }
}
