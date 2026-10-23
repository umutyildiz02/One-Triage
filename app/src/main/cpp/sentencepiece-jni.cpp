// SentencePiece API adapter. Underlying Google SentencePiece: Apache-2.0.
#include <jni.h>
#include <memory>
#include <string>
#include <vector>
#include "sentencepiece_processor.h"
using sentencepiece::SentencePieceProcessor;
static std::string bytes(JNIEnv* e, jbyteArray a) {
    if (!a) return {};
    std::string s(e->GetArrayLength(a), '\0');
    e->GetByteArrayRegion(a, 0, s.size(), reinterpret_cast<jbyte*>(s.data()));
    return s;
}
static void fail(JNIEnv* e) {
    e->ThrowNew(e->FindClass("java/lang/IllegalStateException"), "SentencePiece operation failed");
}
extern "C" JNIEXPORT jlong JNICALL
Java_com_vitaltriage_app_NllbTokenizer_nativeOpen(JNIEnv* e, jobject, jbyteArray path) {
    auto p = std::make_unique<SentencePieceProcessor>();
    if (!p->Load(bytes(e,path)).ok()) { fail(e); return 0; }
    return reinterpret_cast<jlong>(p.release());
}
extern "C" JNIEXPORT jintArray JNICALL
Java_com_vitaltriage_app_NllbTokenizer_nativeEncode(JNIEnv* e, jobject, jlong h, jbyteArray text) {
    auto* p = reinterpret_cast<SentencePieceProcessor*>(h);
    std::vector<int> ids;
    if (!p || !p->Encode(bytes(e,text), &ids).ok()) { fail(e); return nullptr; }
    auto a=e->NewIntArray(ids.size());
    if (a) e->SetIntArrayRegion(a,0,ids.size(),ids.data());
    return a;
}
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_vitaltriage_app_NllbTokenizer_nativeDecode(JNIEnv* e, jobject, jlong h, jintArray a) {
    auto* p = reinterpret_cast<SentencePieceProcessor*>(h);
    if (!p || !a) { fail(e); return nullptr; }
    std::vector<int> ids(e->GetArrayLength(a));
    e->GetIntArrayRegion(a,0,ids.size(),ids.data());
    std::string text;
    if (!p->Decode(ids,&text).ok()) { fail(e); return nullptr; }
    auto result=e->NewByteArray(text.size());
    if(result)e->SetByteArrayRegion(result,0,text.size(),reinterpret_cast<const jbyte*>(text.data()));
    return result;
}
extern "C" JNIEXPORT void JNICALL
Java_com_vitaltriage_app_NllbTokenizer_nativeClose(JNIEnv*, jobject, jlong h) {
    delete reinterpret_cast<SentencePieceProcessor*>(h);
}
