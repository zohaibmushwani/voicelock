#include <jni.h>
#include <vector>
#include <numeric>
#include "kaldi-native-fbank/csrc/feature-fbank.h"
#include "kaldi-native-fbank/csrc/online-feature.h"

extern "C"
JNIEXPORT jfloatArray JNICALL
Java_com_example_voicelock_speakerid_FBankExtractor_computeFBank(
    JNIEnv *env,
    jobject thiz,
    jfloatArray pcm_audio) {

    jsize len = env->GetArrayLength(pcm_audio);
    if (len == 0) {
        return env->NewFloatArray(0);
    }

    jfloat *pcm_ptr = env->GetFloatArrayElements(pcm_audio, nullptr);

    // Configuration: 16kHz sample rate, 25ms window, 10ms hop, 80-bin FBank
    // Parameters verified against ecapa_config.yaml
    knf::FbankOptions opts;
    opts.frame_opts.samp_freq = 16000.0f;
    opts.frame_opts.frame_length_ms = 25.0f;
    opts.frame_opts.frame_shift_ms = 10.0f;
    opts.frame_opts.dither = 1.0f; // As per ecapa_config.yaml
    opts.mel_opts.num_bins = 80;

    knf::OnlineFbank fbank(opts);
    fbank.AcceptWaveform(16000.0f, pcm_ptr, len);
    fbank.InputFinished();

    env->ReleaseFloatArrayElements(pcm_audio, pcm_ptr, JNI_ABORT);

    int32_t num_frames = fbank.NumFramesReady();
    int32_t dim = opts.mel_opts.num_bins;

    if (num_frames == 0) {
        return env->NewFloatArray(0);
    }

    std::vector<float> features(num_frames * dim);
    for (int32_t i = 0; i < num_frames; ++i) {
        const float *frame = fbank.GetFrame(i);
        for (int32_t j = 0; j < dim; ++j) {
            features[i * dim + j] = frame[j];
        }
    }

    // Per-utterance Cepstral Mean Normalization (CMN)
    // Subtract the mean of each bin across all frames in the utterance.
    for (int32_t j = 0; j < dim; ++j) {
        double sum = 0.0;
        for (int32_t i = 0; i < num_frames; ++i) {
            sum += features[i * dim + j];
        }
        float mean = static_cast<float>(sum / num_frames);
        for (int32_t i = 0; i < num_frames; ++i) {
            features[i * dim + j] -= mean;
        }
    }

    jfloatArray result = env->NewFloatArray(features.size());
    env->SetFloatArrayRegion(result, 0, (jsize)features.size(), features.data());

    return result;
}
