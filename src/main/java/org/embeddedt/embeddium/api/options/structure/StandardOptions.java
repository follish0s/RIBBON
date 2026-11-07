package org.embeddedt.embeddium.api.options.structure;

import org.embeddedt.embeddium.impl.Embeddium;
import net.minecraft.resources.Identifier;
import org.embeddedt.embeddium.api.options.OptionIdentifier;

public final class StandardOptions {
    public static class Group {
        public static final Identifier RENDERING = Identifier.fromNamespaceAndPath("minecraft", "rendering");
        public static final Identifier WINDOW = Identifier.fromNamespaceAndPath("minecraft", "window");
        public static final Identifier INDICATORS = Identifier.fromNamespaceAndPath("minecraft", "indicators");
        public static final Identifier GRAPHICS = Identifier.fromNamespaceAndPath("minecraft", "graphics");
        public static final Identifier MIPMAPS = Identifier.fromNamespaceAndPath("minecraft", "mipmaps");
        public static final Identifier DETAILS = Identifier.fromNamespaceAndPath("minecraft", "details");
        public static final Identifier CHUNK_UPDATES = Identifier.fromNamespaceAndPath(Embeddium.MODID, "chunk_updates");
        public static final Identifier RENDERING_CULLING = Identifier.fromNamespaceAndPath(Embeddium.MODID, "rendering_culling");
        public static final Identifier CPU_SAVING = Identifier.fromNamespaceAndPath(Embeddium.MODID, "cpu_saving");
        public static final Identifier SORTING = Identifier.fromNamespaceAndPath(Embeddium.MODID, "sorting");
        public static final Identifier LIGHTING = Identifier.fromNamespaceAndPath(Embeddium.MODID, "lighting");
    }

    public static class Pages {
        public static final OptionIdentifier<Void> GENERAL = OptionIdentifier.create(Embeddium.MODID, "general");
        public static final OptionIdentifier<Void> QUALITY = OptionIdentifier.create(Embeddium.MODID, "quality");
        public static final OptionIdentifier<Void> PERFORMANCE = OptionIdentifier.create(Embeddium.MODID, "performance");
        public static final OptionIdentifier<Void> ADVANCED = OptionIdentifier.create(Embeddium.MODID, "advanced");
    }

    public static class Option {
        public static final Identifier RENDER_DISTANCE = Identifier.fromNamespaceAndPath("minecraft", "render_distance");
        public static final Identifier SIMULATION_DISTANCE = Identifier.fromNamespaceAndPath("minecraft", "simulation_distance");
        public static final Identifier BRIGHTNESS = Identifier.fromNamespaceAndPath("minecraft", "brightness");
        public static final Identifier GUI_SCALE = Identifier.fromNamespaceAndPath("minecraft", "gui_scale");
        public static final Identifier FULLSCREEN = Identifier.fromNamespaceAndPath("minecraft", "fullscreen");
        public static final Identifier FULLSCREEN_RESOLUTION = Identifier.fromNamespaceAndPath("minecraft", "fullscreen_resolution");
        public static final Identifier VSYNC = Identifier.fromNamespaceAndPath("minecraft", "vsync");
        public static final Identifier MAX_FRAMERATE = Identifier.fromNamespaceAndPath("minecraft", "max_frame_rate");
        public static final Identifier VIEW_BOBBING = Identifier.fromNamespaceAndPath("minecraft", "view_bobbing");
        public static final Identifier ATTACK_INDICATOR = Identifier.fromNamespaceAndPath("minecraft", "attack_indicator");
        public static final Identifier AUTOSAVE_INDICATOR = Identifier.fromNamespaceAndPath("minecraft", "autosave_indicator");
        public static final Identifier GRAPHICS_MODE = Identifier.fromNamespaceAndPath("minecraft", "graphics_mode");
        public static final Identifier CLOUDS = Identifier.fromNamespaceAndPath("minecraft", "clouds");
        public static final Identifier WEATHER = Identifier.fromNamespaceAndPath("minecraft", "weather");
        public static final Identifier LEAVES = Identifier.fromNamespaceAndPath("minecraft", "leaves");
        public static final Identifier PARTICLES = Identifier.fromNamespaceAndPath("minecraft", "particles");
        public static final Identifier SMOOTH_LIGHT = Identifier.fromNamespaceAndPath("minecraft", "smooth_lighting");
        public static final Identifier BIOME_BLEND = Identifier.fromNamespaceAndPath("minecraft", "biome_blend");
        public static final Identifier ENTITY_DISTANCE = Identifier.fromNamespaceAndPath("minecraft", "entity_distance");
        public static final Identifier ENTITY_SHADOWS = Identifier.fromNamespaceAndPath("minecraft", "entity_shadows");
        public static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath("minecraft", "vignette");
        public static final Identifier MIPMAP_LEVEL = Identifier.fromNamespaceAndPath("minecraft", "mipmap_levels");
        public static final Identifier CHUNK_UPDATE_THREADS = Identifier.fromNamespaceAndPath(Embeddium.MODID, "chunk_update_threads");
        public static final Identifier DEFFER_CHUNK_UPDATES = Identifier.fromNamespaceAndPath(Embeddium.MODID, "defer_chunk_updates");
        public static final Identifier BLOCK_FACE_CULLING = Identifier.fromNamespaceAndPath(Embeddium.MODID, "block_face_culling");
        public static final Identifier COMPACT_VERTEX_FORMAT = Identifier.fromNamespaceAndPath(Embeddium.MODID, "compact_vertex_format");
        public static final Identifier FOG_OCCLUSION = Identifier.fromNamespaceAndPath(Embeddium.MODID, "fog_occlusion");
        public static final Identifier ENTITY_CULLING = Identifier.fromNamespaceAndPath(Embeddium.MODID, "entity_culling");
        public static final Identifier ANIMATE_VISIBLE_TEXTURES = Identifier.fromNamespaceAndPath(Embeddium.MODID, "animate_only_visible_textures");
        public static final Identifier NO_ERROR_CONTEXT = Identifier.fromNamespaceAndPath(Embeddium.MODID, "no_error_context");
        public static final Identifier PERSISTENT_MAPPING = Identifier.fromNamespaceAndPath(Embeddium.MODID, "persistent_mapping");
        public static final Identifier CPU_FRAMES_AHEAD = Identifier.fromNamespaceAndPath(Embeddium.MODID, "cpu_render_ahead_limit");
        public static final Identifier TRANSLUCENT_FACE_SORTING = Identifier.fromNamespaceAndPath(Embeddium.MODID, "translucent_face_sorting");
        public static final Identifier USE_QUAD_NORMALS_FOR_LIGHTING = Identifier.fromNamespaceAndPath(Embeddium.MODID, "use_quad_normals_for_lighting");
        public static final Identifier RENDER_PASS_OPTIMIZATION = Identifier.fromNamespaceAndPath(Embeddium.MODID, "render_pass_optimization");
    }
}
