Vendored Kotlin API of sherpa-onnx v1.13.8 (https://github.com/k2-fsa/sherpa-onnx, Apache-2.0, see
`app/src/main/assets/licenses/sherpa-onnx-LICENSE.txt`). Files copied unchanged from `sherpa-onnx/kotlin-api/`
at tag v1.13.8, except that `OnlineRecognizer.kt` is cut after the `OnlineRecognizer` class (the model-preset
helper functions were dropped). The JNI field names must match the native library in `app/src/main/jniLibs/*/libsherpa-onnx-jni.so`,
which is the prebuilt `sherpa-onnx-v1.13.8-android-static-link-onnxruntime.tar.bz2` release (onnxruntime statically linked).
