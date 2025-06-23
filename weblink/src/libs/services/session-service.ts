import {
  createStore,
  produce,
  reconcile,
  SetStoreFunction,
} from "solid-js/store";
import { PeerSession } from "../core/session";
import { ClientID, ClientInfo } from "../core/type";
import {
  ClientService,
  TransferClient,
} from "../core/services/type";
import {
  Accessor,
  createEffect,
  createSignal,
  Setter,
} from "solid-js";
import {
  SendClipboardMessage,
  StorageMessage,
} from "@/libs/core/message";
import { v4 } from "uuid";
import { getIceServers } from "@/libs/core/store";
import { appOptions } from "@/options";
import { catchErrorAsync, catchErrorSync } from "../catch";

class SessionService {
  readonly sessions: Record<ClientID, PeerSession>;
  readonly clientViewData: Record<ClientID, ClientInfo>;
  private setSessions: SetStoreFunction<
    Record<ClientID, PeerSession>
  >;
  private setClientViewData: SetStoreFunction<
    Record<ClientID, ClientInfo>
  >;
  private service?: ClientService;

  get clientService() {
    return this.service;
  }

  clientServiceStatus: Accessor<
    "connecting" | "connected" | "disconnected"
  >;

  private setClientServiceStatus: Setter<
    "connecting" | "connected" | "disconnected"
  >;

  iceServers: Promise<RTCIceServer[]>;

  constructor() {
    const [sessions, setSessions] = createStore<
      Record<ClientID, PeerSession>
    >({});
    this.sessions = sessions;
    this.setSessions = setSessions;
    const [clientInfo, setClientInfo] = createStore<
      Record<ClientID, ClientInfo>
    >({});
    this.clientViewData = clientInfo;
    this.setClientViewData = setClientInfo;
    const [clientServiceStatus, setClientServiceStatus] =
      createSignal<
        "connecting" | "connected" | "disconnected"
      >("disconnected");
    this.clientServiceStatus = clientServiceStatus;
    this.setClientServiceStatus = setClientServiceStatus;

    this.iceServers = getIceServers();
  }

  updateIceServers() {
    this.iceServers = getIceServers();
  }

  setClipboard(message: SendClipboardMessage) {
    this.setClientViewData(
      message.client,
      produce((state) => {
        state.clipboard = [
          ...(state.clipboard ?? []),
          message,
        ];
      }),
    );
  }

  setStorage(message: StorageMessage) {
    this.setClientViewData(
      message.client,
      produce((state) => {
        state.storage = [...(message.data ?? [])];
      }),
    );
  }

  setClientService(cs: ClientService) {
    if (this.service) {
      console.warn(
        `client service already set, destory old service`,
      );
      this.removeService();
    }
    this.service = cs;

    cs.addEventListener("statuschange", (ev) => {
      this.setClientServiceStatus(ev.detail);
    });
  }

  removeService() {
    this.service?.close();
    this.service = undefined;
  }

  removeSession(target: ClientID) {
    const session = this.sessions[target];
    if (!session) {
      console.log(
        `can not destory session, session ${target} not found`,
      );
      return;
    }
    session.close();
    this.service?.removeSender(target);
    this.setClientViewData(target, undefined!);
    this.setSessions(target, undefined!);
  }

  requestStorage(client: ClientID) {
    const session = this.sessions[client];
    if (!session) {
      console.warn(
        `[SessionService] request storage, session ${client} not found`,
      );
      return;
    }
    session.sendMessage({
      type: "request-storage",
      id: v4(),
      createdAt: Date.now(),
      client: session.clientId,
      target: session.targetClientId,
    });
  }

  async addClient(client: TransferClient) {
    if (!this.service) {
      throw new Error(
        `can not add client: ${client.clientId}, client service not found`,
      );
    }
    if (this.sessions[client.clientId]) {
      throw new Error(
        `client ${client.clientId} has already created`,
      );
    }
    const polite =
      this.service.info.createdAt < client.createdAt;
    const sender = this.service.createSender(
      client.clientId,
    );
    if (!sender) {
      throw new Error(
        `can not create sender for client: ${client.clientId}`,
      );
    }
    const session = new PeerSession(sender, {
      polite,
      iceServers: await this.iceServers,
      relayOnly:
        appOptions.servers.turns.length > 0 &&
        appOptions.relayOnly,
    });

    this.setClientViewData(client.clientId, {
      ...client,
      onlineStatus: "offline",
      messageChannel: false,
    } satisfies ClientInfo);
    this.setSessions(client.clientId, session);

    const controller = new AbortController();

    session.addEventListener("peerconnectioninit", (ev) => {
      const pc = ev.detail;
      pc.getSenders().forEach((sender) => {
        switch (sender.track?.kind) {
          case "audio": {
            const audioParameters = changeAudioEncoding(
              sender.getParameters(),
            );
            if (audioParameters) {
              sender
                .setParameters(audioParameters)
                .catch((e) => {
                  console.error(
                    `set audio parameters error: ${e}`,
                  );
                });
            }
            break;
          }
          case "video": {
            const videoParameters = changeVideoEncoding(
              sender.getParameters(),
            );
            if (videoParameters) {
              sender
                .setParameters(videoParameters)
                .catch((e) => {
                  console.error(
                    `set video parameters error: ${e}`,
                  );
                });
            }
            break;
          }
        }
      });
    });

    session.addEventListener(
      "statuschange",
      (ev) => {
        console.log(`session status change`, ev.detail);
        switch (ev.detail) {
          case "created":
            break;
          case "connecting":
            this.setClientViewData(
              client.clientId,
              "onlineStatus",
              "connecting",
            );
            break;
          case "connected":
            this.setClientViewData(
              client.clientId,
              "onlineStatus",
              "online",
            );
            break;
          case "reconnecting":
            this.setClientViewData(
              client.clientId,
              "onlineStatus",
              "reconnecting",
            );
            break;
          case "disconnected":
            this.setClientViewData(
              client.clientId,
              "onlineStatus",
              "offline",
            );
            break;
          case "closed":
            this.setClientViewData(
              client.clientId,
              "onlineStatus",
              "offline",
            );
            controller.abort();
            this.removeSession(session.clientId);
            break;
        }
      },
      { signal: controller.signal },
    );

    session.addEventListener(
      "error",
      (ev) => {
        console.error(
          `session ${client.clientId} error`,
          ev.detail,
        );
      },
      { signal: controller.signal },
    );

    session.addEventListener(
      "remotestreamchange",
      (ev) => {
        this.setClientViewData(
          client.clientId,
          "stream",
          reconcile(ev.detail ?? undefined),
        );
      },
      { signal: controller.signal },
    );

    session.addEventListener(
      "messagechannelchange",
      (ev) => {
        if (this.clientViewData[client.clientId]) {
          this.setClientViewData(
            client.clientId,
            "messageChannel",
            ev.detail === "ready",
          );
        }
      },
    );

    return session;
  }

  destoryAllSession() {
    Object.values(this.sessions).forEach((session) =>
      session.close(),
    );
    this.setSessions(reconcile({}));
    this.setClientViewData(reconcile({}));

    this.service?.close();
    this.service = undefined;
  }
}

let sessionService: SessionService;

createEffect(() => {
  if (sessionService && appOptions.servers.turns) {
    sessionService.updateIceServers();
  }
});

sessionService = new SessionService();

function changeAudioEncoding(
  parameters: RTCRtpSendParameters,
): RTCRtpSendParameters | null {
  if (!parameters.encodings) {
    parameters.encodings = [{ active: true }];
  }
  const encoding = parameters.encodings[0] ?? {};
  encoding.active = true;
  // encoding.maxBitrate = appOptions.audioMaxBitrate;
  encoding.priority = "high";
  encoding.networkPriority = "high";
  return parameters;
}

function changeVideoEncoding(
  parameters: RTCRtpSendParameters,
): RTCRtpSendParameters | null {
  parameters.degradationPreference =
    appOptions.degradationPreference ?? "balanced";
  if (!parameters.encodings) {
    parameters.encodings = [{ active: true }];
  }
  const encoding = parameters.encodings[0] ?? {};
  encoding.active = true;
  encoding.maxBitrate = appOptions.videoMaxBitrate;
  encoding.priority = "high";
  encoding.networkPriority = "high";
  return parameters;
}

createEffect(() => {
  appOptions.videoMaxBitrate;
  appOptions.degradationPreference;
  Object.values(sessionService.sessions).forEach(
    (session) => {
      session.peerConnection
        ?.getSenders()
        .forEach((sender) => {
          switch (sender.track?.kind) {
            case "audio":
              const audioParameters = changeAudioEncoding(
                sender.getParameters(),
              );
              if (audioParameters) {
                sender
                  .setParameters(audioParameters)
                  .then(() => {
                    console.log(
                      `set audio parameters success, encoding:`,
                      audioParameters.encodings?.[0],
                    );
                  })
                  .catch((e) => {
                    console.error(
                      `set audio parameters error: ${e}`,
                    );
                  });
              }
              break;
            case "video":
              const videoParameters = changeVideoEncoding(
                sender.getParameters(),
              );
              if (videoParameters) {
                sender
                  .setParameters(videoParameters)
                  .then(() => {
                    console.log(
                      `set video parameters success, encoding:`,
                      videoParameters.encodings?.[0],
                    );
                  })
                  .catch((e) => {
                    console.error(
                      `set video parameters error: ${e}`,
                    );
                  });
              }
              break;
          }
        });
    },
  );
});

export { sessionService };
