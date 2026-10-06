/*
 * LD_PRELOAD shim for QEMU in Termux (non-root Android).
 *
 * An Android app cannot scan /dev/bus/usb or bind the uevent netlink socket,
 * so a plain libusb_init() can fail before QEMU ever reaches the device.
 * QEMU only needs libusb_wrap_sys_device() on the fd from termux-usb, so
 * device discovery is switched off for every context QEMU creates.
 */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdio.h>
#include <libusb.h>

int libusb_init(libusb_context **ctx)
{
    static int (*real_init)(libusb_context **);

    if (!real_init) {
        real_init = (int (*)(libusb_context **))dlsym(RTLD_NEXT, "libusb_init");
        if (!real_init) {
            fprintf(stderr, "libusb_nodiscovery: libusb_init not found\n");
            return LIBUSB_ERROR_OTHER;
        }
    }
    /* NULL context = default for all contexts created afterwards (libusb >= 1.0.27). */
    libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY);
    return real_init(ctx);
}
