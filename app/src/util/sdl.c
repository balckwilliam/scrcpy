#include "sdl.h"

SDL_Window *
sc_sdl_create_window(const char *title, int64_t x, int64_t y, int64_t width,
                     int64_t height, int64_t flags) {
    SDL_Window *window = NULL;

    SDL_PropertiesID props = SDL_CreateProperties();
    if (!props) {
        return NULL;
    }

    bool ok =
        SDL_SetStringProperty(props, SDL_PROP_WINDOW_CREATE_TITLE_STRING,
                              title);
    ok &= SDL_SetNumberProperty(props, SDL_PROP_WINDOW_CREATE_X_NUMBER, x);
    ok &= SDL_SetNumberProperty(props, SDL_PROP_WINDOW_CREATE_Y_NUMBER, y);
    ok &= SDL_SetNumberProperty(props, SDL_PROP_WINDOW_CREATE_WIDTH_NUMBER,
                                width);
    ok &= SDL_SetNumberProperty(props, SDL_PROP_WINDOW_CREATE_HEIGHT_NUMBER,
                                height);
    ok &= SDL_SetNumberProperty(props, SDL_PROP_WINDOW_CREATE_FLAGS_NUMBER,
                                flags);

    if (!ok) {
        SDL_DestroyProperties(props);
        return NULL;
    }

    window = SDL_CreateWindowWithProperties(props);
    SDL_DestroyProperties(props);
    return window;
}

struct sc_size
sc_sdl_get_window_size(SDL_Window *window) {
    int width;
    int height;
    SDL_GetWindowSize(window, &width, &height);

    struct sc_size size = {
        .width = width,
        .height = height,
    };
    return size;
}

struct sc_size
sc_sdl_get_window_size_in_pixels(SDL_Window *window) {
    int width;
    int height;
    SDL_GetWindowSizeInPixels(window, &width, &height);

    struct sc_size size = {
        .width = width,
        .height = height,
    };
    return size;
}

void
sc_sdl_set_window_size(SDL_Window *window, struct sc_size size) {
    SDL_SetWindowSize(window, size.width, size.height);
}

struct sc_point
sc_sdl_get_window_position(SDL_Window *window) {
    int x;
    int y;
    SDL_GetWindowPosition(window, &x, &y);

    struct sc_point point = {
        .x = x,
        .y = y,
    };
    return point;
}

void
sc_sdl_set_window_position(SDL_Window *window, struct sc_point point) {
    SDL_SetWindowPosition(window, point.x, point.y);
}

void
sc_sdl_show_window(SDL_Window *window) {
    SDL_ShowWindow(window);
}

void
sc_sdl_hide_window(SDL_Window *window) {
    SDL_HideWindow(window);
}

void
sc_sdl_restore_window(SDL_Window *window) {
    SDL_RestoreWindow(window);
}

bool
sc_sdl_render_clear(SDL_Renderer *renderer) {
    return SDL_RenderClear(renderer);
}

void
sc_sdl_render_present(SDL_Renderer *renderer) {
    SDL_RenderPresent(renderer);
}
