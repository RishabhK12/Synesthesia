#include <jni.h>
#include <sentencepiece_processor.h>
#include <string>
#include <vector>

// All user input is UTF-8 from Java, avoiding JNI modified-UTF8 ambiguities.
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_hackgt_behindalert_listen_NameTokenizer_encode(JNIEnv* env, jclass, jbyteArray model, jbyteArray text) {
    auto bytes = [env](jbyteArray a) {
        std::string out(env->GetArrayLength(a), '\0');
        env->GetByteArrayRegion(a, 0, out.size(), reinterpret_cast<jbyte*>(&out[0]));
        return out;
    };
    sentencepiece::SentencePieceProcessor processor;
    auto status = processor.LoadFromSerializedProto(bytes(model));
    std::vector<std::string> pieces;
    if (status.ok()) status = processor.Encode(bytes(text), &pieces);
    if (!status.ok()) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), status.ToString().c_str());
        return nullptr;
    }
    jobjectArray result = env->NewObjectArray(pieces.size(), env->FindClass("java/lang/String"), nullptr);
    for (size_t i = 0; i < pieces.size(); ++i) {
        jstring piece = env->NewStringUTF(pieces[i].c_str());
        env->SetObjectArrayElement(result, i, piece);
        env->DeleteLocalRef(piece);
    }
    return result;
}
