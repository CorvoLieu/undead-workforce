package dev.corvo.undeadworkforce.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.corvo.undeadworkforce.UndeadWorkforce;
import dev.corvo.undeadworkforce.minion.Minions;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;

/** Draws a gold collar with a diamond pendant around a minion's neck, following the body's pose. */
public class CollarLayer<T extends Mob, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(UndeadWorkforce.id("zombie_collar"), "main");
    private static final ResourceLocation TEXTURE = UndeadWorkforce.id("textures/entity/zombie_collar.png");

    private final ModelPart collar;

    public CollarLayer(RenderLayerParent<T, M> parent, EntityModelSet models) {
        super(parent);
        this.collar = models.bakeLayer(LAYER);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        // Body cube is 8x12x4 starting at the neck (y=0); the band wraps it slightly larger.
        root.addOrReplaceChild("band",
                CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -0.5F, -2.5F, 9, 2, 5), PartPose.ZERO);
        root.addOrReplaceChild("pendant",
                CubeListBuilder.create().texOffs(0, 8).addBox(-1.0F, 1.5F, -3.0F, 2, 2, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 16);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible() || !Minions.isWearingCollar(entity)) {
            return;
        }
        pose.pushPose();
        if (getParentModel().young) {
            // Mirror AgeableListModel's baby body transform (babyBodyScale 2, bodyYOffset 24).
            pose.scale(0.5F, 0.5F, 0.5F);
            pose.translate(0.0F, 1.5F, 0.0F);
        }
        getParentModel().body.translateAndRotate(pose);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        collar.render(pose, buffer, light, LivingEntityRenderer.getOverlayCoords(entity, 0.0F));
        pose.popPose();
    }
}
