// signaling/websocket-signaling-service.ts
import {
  RawSignal,
  ClientSignal,
  SignalingService,
  SignalingServiceEventMap,
  SignalingServiceStatus,
} from "../type";
import {
  encryptData,
  decryptData,
} from "@/libs/core/utils/encrypt/e2e";
import {
  EventHandler,
  MultiEventEmitter,
} from "@/libs/utils/event-emitter";

export class WebSocketSignalingService
  implements SignalingService
{
  private eventEmitter: MultiEventEmitter<SignalingServiceEventMap> =
    new MultiEventEmitter();
  private socket: WebSocket;
  private _clientId: string;
  private _targetClientId: string;
  private _status: SignalingServiceStatus = "init";
  private password: string | null = null;
  private controller: AbortController | null = null;
  constructor(
    socket: WebSocket,
    clientId: string,
    targetClientId: string,
    password: string | null = null,
  ) {
    this.socket = socket;
    this._clientId = clientId;
    this._targetClientId = targetClientId;
    this.password = password;

    this.setSocket(socket);
  }

  get status(): SignalingServiceStatus {
    return this._status;
  }

  private setSocket(socket: WebSocket) {
    if (this.controller) {
      this.controller.abort();
      this.controller = null;
    }
    const controller = new AbortController();
    const handleOpen = async () => {
      this.setStatus("connected");
    };
    if (socket.readyState === WebSocket.OPEN) {
      handleOpen();
    } else {
      socket.addEventListener("open", handleOpen, {
        once: true,
        signal: controller.signal,
      });
    }
    socket.addEventListener(
      "close",
      () => {
        this.setStatus("disconnected");
      },
      {
        once: true,
        signal: controller.signal,
      },
    );
    socket.addEventListener("message", this.onMessage, {
      signal: controller.signal,
    });

    this.controller = controller;
    this.socket = socket;
  }

  addEventListener<
    K extends keyof SignalingServiceEventMap,
  >(
    event: K,
    callback: EventHandler<SignalingServiceEventMap[K]>,
    options?: boolean | AddEventListenerOptions,
  ): void {
    return this.eventEmitter.addEventListener(
      event,
      callback,
      options,
    );
  }

  removeEventListener<
    K extends keyof SignalingServiceEventMap,
  >(
    event: K,
    callback: EventHandler<SignalingServiceEventMap[K]>,
    options?: boolean | EventListenerOptions,
  ): void {
    return this.eventEmitter.removeEventListener(
      event,
      callback,
      options,
    );
  }

  dispatchEvent<K extends keyof SignalingServiceEventMap>(
    event: K,
    data: SignalingServiceEventMap[K],
  ): boolean {
    return this.eventEmitter.dispatchEvent(event, data);
  }

  resetSocket(socket: WebSocket) {
    this.socket.removeEventListener(
      "message",
      this.onMessage,
    );
    this.setSocket(socket);
  }

  get clientId(): string {
    return this._clientId;
  }

  get targetClientId(): string {
    return this._targetClientId;
  }

  setStatus(status: SignalingServiceStatus) {
    this._status = status;
    if (status !== "init") {
      this.dispatchEvent("statuschange", status);
    }
  }

  async sendSignal(signal: RawSignal): Promise<void> {
    if (this.socket.readyState !== WebSocket.OPEN) {
      throw new Error(
        `[WebSocketSignalingService] socket is not open`,
      );
    }

    if (this.password) {
      signal.data = await encryptData(
        this.password,
        signal.data,
      );
    }

    const message = {
      type: "message",
      data: {
        type: signal.type,
        targetClientId: this._targetClientId,
        clientId: this._clientId,
        data: signal.data,
      } as ClientSignal,
    };

    this.socket.send(JSON.stringify(message));
  }

  private onMessage = async (event: MessageEvent) => {
    const signal: RawSignal = JSON.parse(event.data);
    if (signal.type !== "message") return;

    const message = signal.data as ClientSignal;
    // Check if the signal is intended for this client
    if (
      message.targetClientId &&
      message.targetClientId !== this._clientId
    )
      return;

    // Check if the signal is from the target client
    if (message.clientId !== this._targetClientId) return;

    if (this.password) {
      message.data = await decryptData(
        this.password,
        message.data,
      );
    }
    message.data = JSON.parse(message.data);

    this.dispatchEvent("signal", message);
  };

  close() {
    this.setStatus("closed");
    this.eventEmitter.clearListeners();
    this.socket.removeEventListener(
      "message",
      this.onMessage,
    );
  }
}
