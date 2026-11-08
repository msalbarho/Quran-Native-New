# Vendored whisper.cpp

These are the sources of **whisper.cpp v1.7.6** (https://github.com/ggml-org/whisper.cpp, tag `v1.7.6`, MIT, see `LICENSE`).
Only the parts needed by the app are kept: `src/`, `include/` and `ggml/`. They are vendored so the native library
(`libquranwhisper.so`) builds offline with the NDK; `../CMakeLists.txt` and `../whisper_jni.cpp` are the app's own glue.

To refresh them from upstream (optional):

```
git clone --depth 1 --branch v1.7.6 https://github.com/ggml-org/whisper.cpp /tmp/whisper.cpp
rm -rf src include ggml && cp -r /tmp/whisper.cpp/{src,include,ggml,LICENSE} .
```
