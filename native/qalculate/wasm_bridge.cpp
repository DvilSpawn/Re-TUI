// SPDX-License-Identifier: GPL-3.0-or-later
// Independently authored portable adapter, sharing Re:TUI JNI engine semantics.
#include <emscripten/emscripten.h>
#include <cerrno>

// Async upstream thread IPC is unused: the host terminates the single Web Worker.
// Do not link Emscripten PIPEFS merely to construct an unused CalculateThread.
extern "C" int pipe2(int*, int) { errno = ENOSYS; return -1; }
extern "C" int pipe(int*) { errno = ENOSYS; return -1; }
#include <libqalculate/Calculator.h>
#include <libqalculate/MathStructure.h>
#include <libqalculate/BuiltinFunctions.h>
#include <libqalculate/Variable.h>
#include <memory>
#include <stdexcept>
#include <string>

static std::string jsonString(const std::string& value) {
    std::string result = "\"";
    const char* hex = "0123456789abcdef";
    for (unsigned char c : value) {
        if (c == '"' || c == '\\') { result += '\\'; result += c; }
        else if (c < 32) { result += "\\u00"; result += hex[c >> 4]; result += hex[c & 15]; }
        else result += c;
    }
    return result + "\"";
}

static std::string lowerAscii(std::string value) {
    for (char& c : value) if (c >= 'A' && c <= 'Z') c = static_cast<char>(c - 'A' + 'a');
    return value;
}

static bool asciiAlpha(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
}

static bool containsUnitToken(const std::string& input, const std::string& token) {
    size_t index = input.find(token);
    while (index != std::string::npos) {
        const size_t end = index + token.size();
        const bool before = index == 0 || !asciiAlpha(input[index - 1]);
        const bool after = end >= input.size() || !asciiAlpha(input[end]);
        if (before && after) return true;
        index = input.find(token, index + 1);
    }
    return false;
}

static bool containsImperialFootAndInch(const std::string& input) {
    const std::string lower = lowerAscii(input);
    const bool foot = containsUnitToken(lower, "ft") || containsUnitToken(lower, "foot") || containsUnitToken(lower, "feet");
    const bool inch = containsUnitToken(lower, "in") || containsUnitToken(lower, "inch") || containsUnitToken(lower, "inches");
    return foot && inch;
}

static bool hasZeroDenominator(const MathStructure& value) {
    if (value.isPower() && value.size() == 2 && value[0].isZero() && value[1].representsNegative()) return true;
    for (size_t i = 0; i < value.size(); ++i) if (hasZeroDenominator(value[i])) return true;
    return false;
}

// Only bounded, pure math is evaluated while typing. User functions and deferred
// variable expressions can hide save/load operations, so they require Enter.
static bool safePreview(const MathStructure& value, int depth = 0) {
    if (depth > 20) return false;
    if (value.isFunction()) {
        switch (value.function()->id()) {
            case FUNCTION_ID_SQRT: case FUNCTION_ID_CBRT: case FUNCTION_ID_ROOT:
            case FUNCTION_ID_EXP: case FUNCTION_ID_LOG: case FUNCTION_ID_LOGN:
            case FUNCTION_ID_SIN: case FUNCTION_ID_COS: case FUNCTION_ID_TAN:
            case FUNCTION_ID_ASIN: case FUNCTION_ID_ACOS: case FUNCTION_ID_ATAN:
            case FUNCTION_ID_ABS: case FUNCTION_ID_ROUND: case FUNCTION_ID_FLOOR:
            case FUNCTION_ID_CEIL: case FUNCTION_ID_TRUNC: case FUNCTION_ID_MIN:
            case FUNCTION_ID_MAX: break;
            default: return false;
        }
    }
    if (value.isPower() && (value.size() != 2 || !value[1].isNumber() ||
        value[1].number() > 1000 || value[1].number() < -1000)) return false;
    if (value.isVariable() && value.variable()->isKnown()) {
        auto* variable = static_cast<KnownVariable*>(value.variable());
        if (variable->isExpression() || !safePreview(variable->get(), depth + 1)) return false;
    }
    for (size_t i = 0; i < value.size(); ++i) if (!safePreview(value[i], depth + 1)) return false;
    return true;
}

extern "C" EMSCRIPTEN_KEEPALIVE const char* retui_evaluate(const char* expression, int degrees, int exact, int preview) {
    static std::string output;
    const auto response = [&](int status, const std::string& text, const std::string& parsed = "") -> const char* {
        output = "{\"status\":" + std::to_string(status) + ",\"text\":" + jsonString(text) + ",\"parsed\":" + jsonString(parsed) + "}";
        return output.c_str();
    };
    try {
        const std::string input = expression ? expression : "";
        if (input.empty() || input.size() > 4096) return response(1, "Invalid expression length");
        if (preview && (input.size() > 256 || input.find_first_of("=:\"'") != std::string::npos)) return response(3, "");
        // Owned by one Web Worker. Worker termination resets all engine state.
        static Calculator* calculator = nullptr;
        if (!calculator) {
            auto instance = std::make_unique<Calculator>(true);
            if (!instance->loadGlobalDefinitions()) throw std::runtime_error("Cannot load definitions");
            instance->useDecimalPoint();
            calculator = instance.release();
            calculator->setPrecision(34);
        }
        calculator->clearMessages();
        EvaluationOptions evaluation;
        evaluation.parse_options.angle_unit = degrees ? ANGLE_UNIT_DEGREES : ANGLE_UNIT_RADIANS;
        evaluation.approximation = exact ? APPROXIMATION_TRY_EXACT : APPROXIMATION_APPROXIMATE;
        evaluation.mixed_units_conversion = containsImperialFootAndInch(input)
                ? MIXED_UNITS_CONVERSION_DEFAULT
                : MIXED_UNITS_CONVERSION_NONE;
        PrintOptions print;
        bool approximate = false;
        print.is_approximate = &approximate;
        print.preserve_precision = false;
        print.number_fraction_format = exact ? FRACTION_FRACTIONAL : FRACTION_DECIMAL;
        print.use_unicode_signs = false;
        print.digit_grouping = DIGIT_GROUPING_NONE;
        std::string parsed;
        if (preview) {
            MathStructure tree;
            calculator->startControl(200);
            calculator->parse(&tree, input, evaluation.parse_options);
            const bool safe = !calculator->aborted() && safePreview(tree);
            calculator->stopControl();
            if (!safe) return response(3, "");
        }
        // Synchronous evaluation is bounded by the host worker watchdog.
        std::string text = calculator->calculateAndPrint(input, preview ? 200 : 0, evaluation, print, preview ? &parsed : nullptr);
        bool error = false;
        std::string errors;
        for (auto* message = calculator->message(); message; message = calculator->nextMessage()) {
            // Qalculate can return a number after ignoring a misplaced operator.
            // Treat that correction as invalid input instead of presenting a misleading success.
            const std::string detail = message->message();
            if (message->type() == MESSAGE_ERROR || detail.find("Misplaced operator") != std::string::npos) {
                error = true;
                if (errors.size() < 16384) {
                    if (!errors.empty()) errors += '\n';
                    errors += message->message();
                }
            }
        }
        if (!error) {
            // Exact evaluation can preserve an undefined quotient as "1 / 0"
            // without an error message. Inspect the evaluated output structure.
            MathStructure rendered;
            calculator->parse(&rendered, text, evaluation.parse_options);
            if (hasZeroDenominator(rendered)) { error = true; errors = "Division by zero."; }
        }
        if (error) text = errors;
        if (text.size() > 65536) {
            error = true;
            text = "Calculation result is too long";
        }
        if (preview && (text.find("timed out") != std::string::npos || text.find("aborted") != std::string::npos || parsed.size() > 4096)) return response(3, "");
        // UTF-8 is returned through JSON; no Android APIs are exposed.
        const int status = error ? 1 : (approximate ? 2 : 0);
        return response(status, text, parsed);
    } catch (const std::exception&) {
        return response(1, "Calculation failed");
    } catch (...) {
        return response(1, "Calculation failed");
    }
}
