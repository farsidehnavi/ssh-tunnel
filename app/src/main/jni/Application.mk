APP_ABI := arm64-v8a armeabi-v7a
APP_PLATFORM := android-26
APP_CFLAGS := -O3 -DPKGNAME=hev/htproxy -DCLSNAME=TProxyService -ffunction-sections -fdata-sections -DFD_SET_DEFINED -DSOCKLEN_T_DEFINED
APP_LDFLAGS := -Wl,--gc-sections
