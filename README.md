# Quran Native New

## الترخيص

هذا المشروع متاح للاستفادة منه ودراسته وتطويره والمساهمة فيه بما يخدم كتاب الله تعالى. كود المشروع الأصلي متاح للاستخدام والتعديل وإعادة التوزيع والاستخدام التجاري وفق ترخيص **MIT**.

نصوص وبيانات القرآن المضمّنة، والترجمات، والخطوط، والصور، وصور القراء، والصوت والخدمات، وغيرها من المواد الخارجية لا تُعاد ترخيصها تحت MIT، وتبقى خاضعة لشروط وحقوق أصحابها.

للاطلاع على النص القانوني الكامل، راجع ملف [LICENSE](LICENSE).

## Recitation checking engines — credits & licences

| Component | Licence | Notes |
|---|---|---|
| [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) v1.13.8 (prebuilt JNI, onnxruntime statically linked; Kotlin API vendored in `com/k2fsa/sherpa/onnx`) | Apache-2.0 | `app/src/main/assets/licenses/sherpa-onnx-LICENSE.txt` |
| [whisper.cpp](https://github.com/ggml-org/whisper.cpp) v1.7.6 (vendored in `app/src/main/cpp/whisper`) | MIT | `app/src/main/cpp/whisper/LICENSE` |
| Zipformer Quran 3.1 model (Quran Lab, gated on Hugging Face) | NPL-1.2 (non-profit use only) | Bundled in `app/src/main/assets/zipformer/` — **private, git-ignored, never commit or publish**. Licence shown in-app (Settings → recitation engine). |
| tarteel-ai/whisper-base-ar-quran (GGML q8_0 conversion, downloaded on demand into app storage) | Apache-2.0 | Not bundled. |

Automatic recitation/tajweed feedback can be wrong; it does not replace a qualified teacher.
