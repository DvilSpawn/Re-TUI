// SPDX-License-Identifier: GPL-3.0-or-later
// Re:TUI's independently authored JNI bridge. Upstream libqalculate is GPL-2.0-or-later.
#include <jni.h>
#include <libqalculate/Calculator.h>
#include <libqalculate/MathStructure.h>
#include <memory>
#include <stdexcept>
#include <string>

extern "C" JNIEXPORT jbyteArray JNICALL
Java_ohi_andre_consolelauncher_calculator_NativeQalculate_evaluate(
        JNIEnv* env, jobject, jbyteArray expression, jboolean degrees, jboolean exact) {
    try {
        const auto size = expression ? env->GetArrayLength(expression) : 0;
        if (size <= 0 || size > 4096) throw std::invalid_argument("Invalid expression length");
        std::string input(size, '\0');
        env->GetByteArrayRegion(expression, 0, size, reinterpret_cast<jbyte*>(input.data()));
        if (env->ExceptionCheck()) return nullptr;
        if (input.find('\0') != std::string::npos) throw std::invalid_argument("NUL in expression");
        // Owned by the dedicated service process, never used concurrently. Process exit
        // releases upstream globals and threads without unsupported pthread cancellation.
        static Calculator* calculator = nullptr;
        if (!calculator) {
            auto instance = std::make_unique<Calculator>(true);
            if (!instance->loadGlobalDefinitions()) throw std::runtime_error("Cannot load definitions");
            instance->useDecimalPoint();
            calculator = instance.release();
        }
        calculator->clearMessages();
        EvaluationOptions evaluation;
        evaluation.parse_options.angle_unit = degrees ? ANGLE_UNIT_DEGREES : ANGLE_UNIT_RADIANS;
        evaluation.approximation = exact ? APPROXIMATION_TRY_EXACT : APPROXIMATION_APPROXIMATE;
        evaluation.mixed_units_conversion = MIXED_UNITS_CONVERSION_NONE;
        PrintOptions print;
        // Synchronous evaluation. The Android service watchdog bounds the entire request,
        // including initialization and formatting, and kills only the native worker process.
        std::string text = calculator->calculateAndPrint(input, 0, evaluation, print);
        bool error = false;
        std::string errors;
        for (auto* message = calculator->message(); message; message = calculator->nextMessage()) {
            if (message->type() == MESSAGE_ERROR) {
                error = true;
                if (errors.size() < 16384) {
                    if (!errors.empty()) errors += '\n';
                    errors += message->message();
                }
            }
        }
        if (error) text = errors;
        if (text.size() > 65536) {
            error = true;
            text = "Calculation result is too long";
        }
        // Standard UTF-8 bytes avoid JNI's modified-UTF-8 restrictions (e.g. emoji).
        const jbyte status = error ? 1 : 0;
        auto result = env->NewByteArray(static_cast<jsize>(text.size() + 1));
        if (!result) return nullptr;
        env->SetByteArrayRegion(result, 0, 1, &status);
        env->SetByteArrayRegion(result, 1, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte*>(text.data()));
        return result;
    } catch (const std::exception& error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Native calculation failed");
        return nullptr;
    }
}
