#ifndef SC_RECT_H
#define SC_RECT_H

#include "common.h"

#include <SDL3/SDL_rect.h>

#include "coords.h"

// Return whether the size is optimal
//
// It is optimal if it does not require black borders to preserve the aspect
// ratio, with rounding applied at pixel boundaries.
bool
sc_rect_is_optimal_size(struct sc_size current_size,
                        struct sc_size content_size);

// Compute the content location, preserving its aspect ratio
void
sc_rect_get_content_location(struct sc_size render_size,
                             struct sc_size content_size, bool can_upscale,
                             SDL_FRect *out);

#endif
