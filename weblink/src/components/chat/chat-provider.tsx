import { localStream } from "@/libs/stream";
import { WebRTCProvider } from "@/libs/core/rtc-context";
import { ParentComponent } from "solid-js";

const ChatProvider: ParentComponent = (props) => {
  return (
    <>
      <WebRTCProvider localStream={localStream()}>
        {props.children}
      </WebRTCProvider>
    </>
  );
};

export default ChatProvider;
