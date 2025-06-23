import {
  createEffect,
  createRoot,
  createSignal,
  untrack,
} from "solid-js";

// 主视频流
const [localStream, setLocalStream] =
  createSignal<MediaStream | null>(null);

// 屏幕共享流
const [displayStream, setDisplayStream] =
  createSignal<MediaStream | null>();

// 摄像头流
const [cameraStream, setCameraStream] =
  createSignal<MediaStream | null>(null);

// 清理流
function cleanupStream(stream: MediaStream | null) {
  if (stream) {
    stream.getTracks().forEach(track => {
      track.stop();
    });
  }
}

createRoot(() => {
  createEffect(() => {
    const currentStream = untrack(localStream);
    const display = displayStream();
    if (currentStream?.id === display?.id) return;

    if (currentStream) {
      currentStream.getTracks().forEach((track) => {
        currentStream.removeTrack(track);
        track.stop();
      });
      setLocalStream(null);
    }

    if (display) {
      display.getTracks().forEach((track) => {
        track.addEventListener("ended", () => {
          console.log(
            `display stream remove track`,
            track.id,
          );
          display.removeTrack(track);

          if (display.getTracks().length === 0) {
            setLocalStream(null);
          }
        });
      });

      setLocalStream(display);
    }
  });

  // 处理摄像头流
  createEffect(() => {
    const camera = cameraStream();
    
    // 清理旧的摄像头流
    if (!camera && untrack(cameraStream)) {
      const oldCamera = untrack(cameraStream);
      if (oldCamera) {
        cleanupStream(oldCamera);
      }
    }
    
    if (camera) {
      // 为摄像头流添加结束事件处理
      camera.getTracks().forEach((track) => {
        track.addEventListener("ended", () => {
          console.log(
            `camera stream track ended`,
            track.id,
          );
          camera.removeTrack(track);

          if (camera.getTracks().length === 0) {
            setCameraStream(null);
          }
        });
      });
    }
  });
});

// 自定义setter，添加清理逻辑
function setDisplayStreamWithCleanup(stream: MediaStream | null) {
  const oldStream = displayStream();
  if (oldStream && oldStream !== stream) {
    cleanupStream(oldStream);
  }
  setDisplayStream(stream);
}

function setCameraStreamWithCleanup(stream: MediaStream | null) {
  const oldStream = cameraStream();
  if (oldStream && oldStream !== stream) {
    cleanupStream(oldStream);
  }
  setCameraStream(stream);
}

// 导出所有流和它们的setter
export { localStream, displayStream, cameraStream };
export const setDisplayStreamExport = setDisplayStreamWithCleanup;
export const setCameraStreamExport = setCameraStreamWithCleanup;

