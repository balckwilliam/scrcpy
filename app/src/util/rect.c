#include "rect.h"

bool
sc_rect_is_optimal_size(struct sc_size current_size,
                        struct sc_size content_size) {
    // The size is optimal if we can recompute one dimension of the current
    // size from the other
    return current_size.height == current_size.width * content_size.height
                                                     / content_size.width
        || current_size.width == current_size.height * content_size.width
                                                     / content_size.height;
}

// Compute the content location, preserving its aspect ratio
void
sc_rect_get_content_location(struct sc_size render_size,
                             struct sc_size content_size, bool can_upscale,
                             SDL_FRect *out) {
    if (sc_rect_is_optimal_size(render_size, content_size)) {
        out->x = 0;
        out->y = 0;
        out->w = render_size.width;
        out->h = render_size.height;
        return;
    }

    if (!can_upscale && content_size.width <= render_size.width
                     && content_size.height <= render_size.height) {
        // Center without upscaling
        out->x = (render_size.width - content_size.width) / 2.f;
        out->y = (render_size.height - content_size.height) / 2.f;
        out->w = content_size.width;
        out->h = content_size.height;
        return;
    }

    bool keep_width = content_size.width * render_size.height
                    > content_size.height * render_size.width;
    if (keep_width) {
        out->x = 0;
        out->w = render_size.width;
        out->h = (float) render_size.width * content_size.height
                                            / content_size.width;
        out->y = (render_size.height - out->h) / 2.f;
    } else {
        out->y = 0;
        out->h = render_size.height;
        out->w = (float) render_size.height * content_size.width
                                             / content_size.height;
        out->x = (render_size.width - out->w) / 2.f;
    }
}
