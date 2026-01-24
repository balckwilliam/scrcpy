#ifndef SC_ICON_H
#define SC_ICON_H

#include "common.h"

#include <SDL3/SDL_surface.h>

SDL_Surface *
sc_icon_load(const char *path);

SDL_Surface *
sc_icon_load_scrcpy(void);

void
sc_icon_destroy(SDL_Surface *icon);

#endif
