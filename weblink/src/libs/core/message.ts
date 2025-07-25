import {
  createStore,
  produce,
  reconcile,
  SetStoreFunction,
} from "solid-js/store";
import { ChunkRange } from "../utils/range";
import { ClientID, FileID, Client } from "./type";
import {
  FileTransferer,
  ProgressValue,
  TransferMode,
} from "./file-transferer";
import { ChunkCache } from "../cache/chunk-cache";
import { Accessor, createSignal, Setter } from "solid-js";
import { ChunkMetaData } from "../cache";

export type MessageID = string;

export interface BaseExchangeMessage {
  id: MessageID;
  type: string;
  createdAt: number;
  client: ClientID;
  target: ClientID;
  status?: "sending" | "received" | "error";
}

export interface BaseStorageMessage
  extends BaseExchangeMessage {
  id: string;
}

export interface TextMessage extends BaseStorageMessage {
  type: "text";
  data: string;
  error?: string;
}

export interface FileTransferMessage
  extends BaseStorageMessage {
  type: "file";
  fid?: FileID;
  fileName: string;
  fileSize: number;
  mimeType?: string;
  lastModified?: number;
  chunkSize: number;
  error?: string;
  progress?: {
    total: number;
    received: number;
  };
  transferStatus?:
    | "init"
    | "transfering"
    | "complete"
    | "paused"
    | "error";
}

export type StoreMessage =
  | TextMessage
  | FileTransferMessage;

export type SendTextMessage = BaseExchangeMessage & {
  type: "send-text";
  data: string;
};

export type CheckMessage = BaseExchangeMessage & {
  type: "check-message";
  mode: "send" | "receive";
  id: MessageID;
};

export type ReadTextMessage = BaseExchangeMessage & {
  type: "read-text";
  id: MessageID;
};

export type RequestFileMessage = BaseExchangeMessage & {
  type: "request-file";
  fid: FileID;
  ranges?: ChunkRange[];
  fileName: string;
  fileSize: number;
  mimeType?: string;
  lastModified?: number;
  chunkSize: number;
  resume: boolean;
};

export type ResumeFileMessage = BaseExchangeMessage & {
  type: "resume-file";
  fid: FileID;
};

export type SendFileMessage = BaseExchangeMessage & {
  type: "send-file";
  fid: FileID;
  fileName: string;
  fileSize: number;
  mimeType?: string;
  lastModified?: number;
  chunkSize: number;
};

export type SendClipboardMessage = BaseExchangeMessage & {
  type: "send-clipboard";
  data: string;
};

export type ErrorMessage = BaseExchangeMessage & {
  type: "error";
  fid?: FileID;
  error: string;
};

export type StorageMessage = BaseExchangeMessage & {
  type: "storage";
  data: ChunkMetaData[];
};

export type RequestStorageMessage = BaseExchangeMessage & {
  type: "request-storage";
};

export type SessionMessage =
  | SendTextMessage
  | CheckMessage
  | ReadTextMessage
  | RequestFileMessage
  | SendFileMessage
  | SendClipboardMessage
  | ErrorMessage
  | StorageMessage
  | RequestStorageMessage
  | ResumeFileMessage;

class MessageStores {
  readonly messages: StoreMessage[];
  readonly clients: Client[];
  readonly db: Promise<IDBDatabase> | IDBDatabase;
  private setMessages: SetStoreFunction<StoreMessage[]>;
  private setClients: SetStoreFunction<Client[]>;
  status: Accessor<"initializing" | "ready">;
  private setStatus: Setter<"initializing" | "ready">;
  private controllers: Record<FileID, AbortController> = {};
  constructor() {
    const [messages, setMessages] = createStore<
      StoreMessage[]
    >([]);
    this.messages = messages;
    this.setMessages = setMessages;

    const [clients, setClients] = createStore<Client[]>([]);
    this.clients = clients;
    this.setClients = setClients;

    this.db = this.initDB();
    const [status, setStatus] = createSignal<
      "initializing" | "ready"
    >("initializing");
    this.status = status;
    this.setStatus = setStatus;
  }

  private timeouts: Record<MessageID, number> = {};

  private clearTimeout(id: MessageID) {
    window.clearTimeout(this.timeouts[id]);
    delete this.timeouts[id];
  }

  private setTimeout(
    id: MessageID,
    timeout: number,
    callback: () => void,
  ) {
    this.timeouts[id] = window.setTimeout(() => {
      this.clearTimeout(id);
      callback();
    }, timeout);
  }

  private async initDB() {
    return new Promise<IDBDatabase>((resolve, reject) => {
      const request = indexedDB.open("message_store");

      request.onupgradeneeded = () => {
        const db = request.result;
        const messageStore = db.createObjectStore(
          "messages",
          {
            keyPath: "id",
          },
        );

        messageStore.createIndex(
          "createdAtIndex",
          "createdAt",
          {
            unique: false,
          },
        );

        db.createObjectStore("clients", {
          keyPath: "clientId",
        });
      };

      request.onsuccess = async () => {
        const db = request.result;
        resolve(db);
        this.loadDB();
      };

      request.onerror = () => {
        reject(request.error);
      };
    });
  }

  private async loadDB() {
    const db = await this.db;
    const index = db
      .transaction("messages", "readonly")
      .objectStore("messages")
      .index("createdAtIndex");

    const promise1 = new Promise<StoreMessage[]>(
      (resolve, reject) => {
        const request = index.getAll();
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
      },
    ).then((messages) => {
      this.setMessages(
        reconcile(
          messages.map((message) => {
            if (message.type === "file") {
              if (message.transferStatus !== "complete") {
                message.transferStatus = "paused";
              }
            }
            return message;
          }),
        ),
      );
    });

    const clientStore = db
      .transaction("clients", "readonly")
      .objectStore("clients");

    const promise2 = new Promise<Client[]>(
      (resolve, reject) => {
        const request = clientStore.getAll();
        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
      },
    ).then((clients) => {
      this.setClients(reconcile(clients));
    });

    return Promise.all([promise1, promise2]).then(() => {
      this.setStatus("ready");
    });
  }

  private async setMessageDB(message: StoreMessage) {
    const db = await this.db;
    let request: IDBRequest<IDBValidKey>;
    return new Promise((resolve, reject) => {
      if (message.type === "text") {
        request = db
          .transaction("messages", "readwrite")
          .objectStore("messages")
          .put({
            ...message,
          });
      } else if (message.type === "file") {
        const { progress, ...storeMessage } = message;

        request = db
          .transaction("messages", "readwrite")
          .objectStore("messages")
          .put(storeMessage);
      }
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  private async removeMessageDB(messageId: MessageID) {
    const db = await this.db;
    const request = db
      .transaction("messages", "readwrite")
      .objectStore("messages")
      .delete(messageId);

    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  private async removeMessagesDB(messageIds: MessageID[]) {
    const db = await this.db;
    const transaction = db.transaction(
      "messages",
      "readwrite",
    );

    const store = transaction.objectStore("messages");

    for (const id of messageIds) {
      store.delete(id);
    }

    return new Promise<void>((resolve, reject) => {
      transaction.oncomplete = () => resolve();
      transaction.onerror = () => reject(transaction.error);
    });
  }

  private async setClientDB(client: Client) {
    const db = await this.db;
    const request = db
      .transaction("clients", "readwrite")
      .objectStore("clients")
      .put(client);
    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  private async removeClientDB(clientId: ClientID) {
    const db = await this.db;
    const request = db
      .transaction("clients", "readwrite")
      .objectStore("clients")
      .delete(clientId);

    return new Promise((resolve, reject) => {
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  private getMessageSetter(index: number) {
    if (this.messages[index]) {
      return (cb: (state: FileTransferMessage) => void) => {
        this.setMessages(
          index,
          produce((state) => {
            cb(state as FileTransferMessage);
            this.setMessageDB(state);
          }),
        );
      };
    }
    return null;
  }

  private getController(fileId: FileID) {
    let controller = this.controllers[fileId];
    if (controller) return controller;

    controller = new AbortController();
    controller.signal.addEventListener(
      "abort",
      () => {
        delete this.controllers[fileId];
      },
      { once: true },
    );
    this.controllers[fileId] = controller;
    return controller;
  }

  setSendMessage(sessionMsg: SessionMessage) {
    let index: number = this.messages.findLastIndex(
      (msg) => msg.id === sessionMsg.id,
    );
    const setStatus = (message: StoreMessage) => {
      this.setMessages(index, "error", undefined);
      this.setMessageDB(this.messages[index]);

      this.setTimeout(message.id, 5000, () => {
        this.setMessages(index, "status", "error");
        this.setMessages(index, "error", "send timeout");
        this.setMessageDB(this.messages[index]);
      });
    };
    if (sessionMsg.type === "send-text") {
      if (index === -1) {
        const message = {
          ...sessionMsg,
          type: "text",
          status: "sending",
        } satisfies TextMessage;
        this.setMessages(
          produce((state) => {
            index = state.push(message) - 1;
            this.setMessageDB(message);
            setStatus(message);
          }),
        );
      }
    } else if (sessionMsg.type === "send-file") {
      if (index === -1) {
        const message = {
          ...sessionMsg,
          type: "file",
          status: "sending",
        } satisfies FileTransferMessage;
        this.setMessages(
          produce((state) => {
            index = state.push(message) - 1;
            this.setMessageDB(message);
            setStatus(message);
          }),
        );
      }
    } else if (sessionMsg.type === "request-file") {
      if (index === -1) {
        const message = {
          id: sessionMsg.id,
          type: "file",
          status: "sending",
          fid: sessionMsg.fid,
          fileName: sessionMsg.fileName,
          fileSize: sessionMsg.fileSize,
          chunkSize: sessionMsg.chunkSize,
          createdAt: sessionMsg.createdAt,
          client: sessionMsg.target,
          target: sessionMsg.client,
          transferStatus: "init",
        } satisfies FileTransferMessage;
        this.setMessages(
          produce((state) => {
            index = state.push(message) - 1;
            this.setMessageDB(message);
            setStatus(message);
          }),
        );
      }
    }
  }

  setReceiveMessage(sessionMsg: SessionMessage) {
    let index: number = this.messages.findIndex(
      (msg) => msg.id === sessionMsg.id,
    );

    const setStatus = (index: number) => {
      this.setMessages(index, "error", undefined);
      this.setMessageDB(this.messages[index]);
    };

    if (sessionMsg.type === "send-text") {
      if (index === -1) {
        const message = {
          ...sessionMsg,
          type: "text",
          status: "received",
        } satisfies TextMessage;
        this.setMessages(
          produce((state) => {
            index = state.push(message) - 1;
            this.setMessageDB(message);
          }),
        );
      }
      setStatus(index);
    } else if (sessionMsg.type === "send-file") {
      if (index === -1) {
        const message = {
          ...sessionMsg,
          type: "file",
          status: "received",
        } satisfies FileTransferMessage;
        this.setMessages(
          produce((state) => {
            index = state.push(message) - 1;
            this.setMessageDB(message);
          }),
        );
      }
      setStatus(index);
    } else if (sessionMsg.type === "request-file") {
      if (index === -1) {
        const message = {
          id: sessionMsg.id,
          type: "file",
          status: "received",
          fid: sessionMsg.fid,
          fileName: sessionMsg.fileName,
          fileSize: sessionMsg.fileSize,
          chunkSize: sessionMsg.chunkSize,
          createdAt: sessionMsg.createdAt,
          client: sessionMsg.target,
          target: sessionMsg.client,
          transferStatus: "init",
        } satisfies FileTransferMessage;
        this.setMessages(
          produce((state) => {
            index = state.push(message) - 1;
            this.setMessageDB(message);
          }),
        );
      }
    } else if (sessionMsg.type === "error") {
      this.clearTimeout(sessionMsg.id);
      this.setMessages(
        index,
        produce((state) => {
          state.status = "error";
          state.error = sessionMsg.error;
          this.setMessageDB(state);
        }),
      );
    } else if (sessionMsg.type === "check-message") {
      if (index !== -1) {
        this.clearTimeout(sessionMsg.id);
        this.setMessages(
          index,
          produce((state) => {
            state.status = "received";
            state.error = undefined;
            this.setMessageDB(state);
          }),
        );
      }
    }
  }

  async addMessage(message: StoreMessage) {
    new Promise((resolve, reject) => {
      this.setMessages(
        produce((state) => {
          state.push(message);
        }),
      );

      this.setMessageDB(message)
        .then(resolve)
        .catch(reject);
    });
  }

  setClient(client: Client) {
    const index = this.clients.findIndex(
      (c) => c.clientId === client.clientId,
    );
    if (index !== -1) {
      this.setClients(index, client);
    } else {
      this.setClients(
        produce((state) => state.push(client)),
      );
    }
    this.setClientDB(client);
  }

  deleteClient(clientId: ClientID) {
    const index = this.clients.findIndex(
      (client) => client.clientId === clientId,
    );
    if (index !== -1) {
      this.setClients(
        produce((state) => state.splice(index, 1)),
      );
      this.removeClientDB(clientId);
      this.deleteMessagesByClient(clientId);
    }
  }

  deleteMessagesByClient(clientId: ClientID) {
    const messageDeletes = this.messages.filter(
      (message) => {
        return (
          message.client === clientId ||
          message.target === clientId
        );
      },
    );

    this.removeMessagesDB(
      messageDeletes.map((message) => message.id),
    );
    this.setMessages((state) =>
      state.filter(
        (message) =>
          message.client !== clientId &&
          message.target !== clientId,
      ),
    );
  }

  addCache(cache: ChunkCache) {
    const controller = this.getController(cache.id);
    const index = this.messages.findLastIndex(
      (msg) => msg.type === "file" && msg.fid === cache.id,
    );
    if (index === -1) {
      console.warn(`cache for message not existed`);
      return false;
    }
    const setter = this.getMessageSetter(index);
    if (!setter) {
      console.warn(`setter for message not existed`);
      return false;
    }

    cache.addEventListener(
      "complete",
      () => {
        controller.abort("complete");
        setter((state) => {
          state.transferStatus = "complete";
        });
      },
      { once: true, signal: controller.signal },
    );
    return true;
  }

  deleteMessage(message: MessageID) {
    const index = this.messages.findIndex(
      (msg) => msg.id === message,
    );
    if (index !== -1) {
      this.setMessages(
        produce((state) => state.splice(index, 1)),
      );
      this.removeMessageDB(message);
      return true;
    }
    return false;
  }

  addTransfer(transferer: FileTransferer) {
    if (this.controllers[transferer.id] !== undefined) {
      console.warn(
        `transferer ${transferer.id} has been added`,
      );
    }
    const index = this.messages.findLastIndex(
      (msg) =>
        msg.type === "file" && msg.fid === transferer.id,
    );
    if (index === -1) {
      console.warn(`transferer for message not existed`);
      return;
    }
    const setter = this.getMessageSetter(index);
    if (!setter) {
      console.warn(`setter for message not existed`);
      return;
    }
    const controller = this.getController(transferer.id);
    if (transferer.mode === TransferMode.Receive) {
      this.addCache(transferer.cache);
    }

    transferer.addEventListener(
      "ready",
      () => {
        setter((state) => {
          state.error = undefined;
          state.transferStatus = "transfering";
        });
      },
      {
        signal: controller.signal,
      },
    );
    transferer.addEventListener(
      "progress",
      (event: CustomEvent<ProgressValue>) => {
        // console.log(`progress`, event.detail);
        const { total, received } = event.detail;
        setter((state) => {
          state.progress = {
            total: total,
            received: received,
          };
          state.transferStatus = "transfering";
        });
      },
      {
        signal: controller.signal,
      },
    );
    transferer.addEventListener(
      "complete",
      () => {
        if (transferer.mode === TransferMode.Send) {
          controller.abort();
          setter((state) => {
            state.status = "received";
            state.transferStatus = "complete";
            state.error = undefined;
          });
        }
      },
      {
        signal: controller.signal,
      },
    );

    transferer.addEventListener(
      "close",
      () => {
        controller.abort();
        setter((state) => {
          if (state.transferStatus !== "complete") {
            state.transferStatus = "paused";
          }
        });
      },
      { signal: controller.signal },
    );

    transferer.addEventListener(
      "error",
      (event: CustomEvent<Error>) => {
        console.error(event.detail);
        controller.abort();
        setter((state) => {
          state.transferStatus = "error";
          state.error = event.detail.message;
        });
      },
      {
        signal: controller.signal,
      },
    );
  }
}

export const messageStores = new MessageStores();
