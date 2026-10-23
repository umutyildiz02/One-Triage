# Narrow build-time adapter for pinned whisper.cpp v1.8.0.
# Keep upstream/submodule sources and MIT notices unchanged. Fail on changed anchors.
get_target_property(VT_WHISPER_DIR whisper SOURCE_DIR)
file(READ "${VT_WHISPER_DIR}/whisper.cpp" VT_WHISPER_SOURCE)
function(vt_replace_once needle replacement)
    string(LENGTH "${VT_WHISPER_SOURCE}" before)
    string(REPLACE "${needle}" "" removed "${VT_WHISPER_SOURCE}")
    string(LENGTH "${removed}" after)
    string(LENGTH "${needle}" expected)
    math(EXPR actual "${before} - ${after}")
    if(NOT actual EQUAL expected)
        message(FATAL_ERROR "Whisper cancellation adapter anchor changed; review pinned source.")
    endif()
    string(REPLACE "${needle}" "${replacement}" updated "${VT_WHISPER_SOURCE}")
    set(VT_WHISPER_SOURCE "${updated}" PARENT_SCOPE)
endfunction()

set(VT_SCOPE [=[
static thread_local ggml_abort_callback vt_abort_callback = nullptr;
static thread_local void * vt_abort_data = nullptr;
struct vt_abort_scope {
    ggml_abort_callback previous_callback;
    void * previous_data;
    vt_abort_scope(ggml_abort_callback callback, void * data)
        : previous_callback(vt_abort_callback), previous_data(vt_abort_data) {
        vt_abort_callback = callback;
        vt_abort_data = data;
    }
    ~vt_abort_scope() {
        vt_abort_callback = previous_callback;
        vt_abort_data = previous_data;
    }
};

]=])
set(VT_FIRST_HELPER [=[static bool ggml_graph_compute_helper(
          struct ggml_cgraph * graph,]=])
vt_replace_once("${VT_FIRST_HELPER}" "${VT_SCOPE}${VT_FIRST_HELPER}")

set(VT_THREADS [=[        auto * fn_set_n_threads = (ggml_backend_set_n_threads_t) ggml_backend_reg_get_proc_address(reg, "ggml_backend_set_n_threads");]=])
set(VT_ABORT [=[
        // Propagate the full-call scope into CPU graph nodes, including auto-language encoding.
        auto * fn_set_abort = reg ? (ggml_backend_set_abort_callback_t)
            ggml_backend_reg_get_proc_address(reg, "ggml_backend_set_abort_callback") : nullptr;
        if (fn_set_abort) fn_set_abort(backend, vt_abort_callback, vt_abort_data);
]=])
vt_replace_once("${VT_THREADS}" "${VT_ABORT}\n${VT_THREADS}")

set(VT_COMPUTE [=[    const bool t = (ggml_backend_sched_graph_compute(sched, graph) == GGML_STATUS_SUCCESS);]=])
set(VT_CLEAR [=[
    // Compute is synchronous. Do not leave a caller-owned pointer on a reusable backend.
    for (int i = 0; i < ggml_backend_sched_get_n_backends(sched); ++i) {
        ggml_backend_t backend = ggml_backend_sched_get_backend(sched, i);
        auto * dev = ggml_backend_get_device(backend);
        auto * reg = dev ? ggml_backend_dev_backend_reg(dev) : nullptr;
        auto * fn = reg ? (ggml_backend_set_abort_callback_t)
            ggml_backend_reg_get_proc_address(reg, "ggml_backend_set_abort_callback") : nullptr;
        if (fn) fn(backend, nullptr, nullptr);
    }
]=])
vt_replace_once("${VT_COMPUTE}" "${VT_COMPUTE}\n${VT_CLEAR}")

set(VT_FULL [=[int whisper_full_with_state(
        struct whisper_context * ctx,
          struct whisper_state * state,
    struct whisper_full_params   params,
                   const float * samples,
                           int   n_samples) {]=])
vt_replace_once("${VT_FULL}" "${VT_FULL}\n    vt_abort_scope vt_scope(params.abort_callback, params.abort_callback_user_data);")

set(VT_PATCHED "${CMAKE_CURRENT_BINARY_DIR}/whisper-vt-abort.cpp")
set(VT_PREVIOUS "")
if(EXISTS "${VT_PATCHED}")
    file(READ "${VT_PATCHED}" VT_PREVIOUS)
endif()
if(NOT VT_PREVIOUS STREQUAL VT_WHISPER_SOURCE)
    file(WRITE "${VT_PATCHED}" "${VT_WHISPER_SOURCE}")
endif()
get_target_property(VT_SOURCES whisper SOURCES)
set(VT_ADAPTED_SOURCES "")
set(VT_SOURCE_REPLACED FALSE)
foreach(source IN LISTS VT_SOURCES)
    if(source STREQUAL "whisper.cpp")
        list(APPEND VT_ADAPTED_SOURCES "${VT_PATCHED}")
        set(VT_SOURCE_REPLACED TRUE)
    else()
        get_filename_component(absolute "${source}" ABSOLUTE BASE_DIR "${VT_WHISPER_DIR}")
        list(APPEND VT_ADAPTED_SOURCES "${absolute}")
    endif()
endforeach()
if(NOT VT_SOURCE_REPLACED)
    message(FATAL_ERROR "Whisper source target changed; cancellation adapter not installed.")
endif()
set_property(TARGET whisper PROPERTY SOURCES ${VT_ADAPTED_SOURCES})
