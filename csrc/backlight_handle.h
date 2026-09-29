/* Native backlight ownership: open before locking, retain until ownership ends.
 * All callers run on the daemon's single control thread. */
#pragma once
#include <fcntl.h>
#include <unistd.h>
#include <sys/stat.h>
static int luma_owned_backlight_fd = -1;
static int luma_backlight_acquire(const char *path, unsigned permissions) {
    if (luma_owned_backlight_fd >= 0)
        return chmod(path, (mode_t)permissions);
    /* Recover a lock left by a killed/older daemon before opening. */
    if (chmod(path, 0644) < 0) return -1;
    int fd = open(path, O_WRONLY | O_CLOEXEC);
    if (fd < 0) {
        int error = errno;
        luma_backlight_write_error("acquire_open", error);
        errno = error;
        return -1;
    }
    if (chmod(path, (mode_t)permissions) < 0) {
        int error = errno;
        close(fd);
        chmod(path, 0644);
        errno = error;
        return -1;
    }
    luma_owned_backlight_fd = fd;
    luma_backlight_fd = fd;
    return 0;
}
static int luma_backlight_release(const char *path, unsigned permissions) {
    int result = chmod(path, (mode_t)permissions), error = errno;
    int fd = luma_owned_backlight_fd;
    luma_owned_backlight_fd = -1;
    if (fd >= 0) {
        if (luma_backlight_fd == fd) luma_backlight_fd = -1;
        if (close(fd) < 0 && result == 0) {
            result = -1;
            error = errno;
            luma_backlight_write_error("release_close", error);
        }
    }
    errno = error;
    return result;
}
