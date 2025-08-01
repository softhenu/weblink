import { makePersisted } from "@solid-primitives/storage";
import { createStore } from "solid-js/store";
import { fakerZH_CN } from "@faker-js/faker";
import { Client } from "./type";
import { v4 } from "uuid";
import { generateHMAC } from "./utils/encrypt/hmac";
import { appOptions, TurnServerOptions } from "@/options";
import { catchErrorAsync } from "../catch";

export interface ClientProfile extends Client {
  roomId: string;
  password: string | null;
  autoJoin: boolean;
  initalJoin: boolean;
}

/**
 * parse turn server options to RTCIceServer
 * @param turn - turn server options
 * @returns RTCIceServer
 * @throws Error
 */
export async function parseTurnServer(
  turn: TurnServerOptions,
): Promise<RTCIceServer> {
  const { authMethod, username, password, url } = turn;
  if (authMethod === "hmac") {
    const timestamp =
      Math.floor(Date.now() / 1000) + 24 * 3600;
    const hmacUsernameArr = [timestamp.toString()];
    if (username.trim().length !== 0) {
      hmacUsernameArr.push(username);
    }
    const hmacUsername = hmacUsernameArr.join(":");
    const credential = await generateHMAC(
      password,
      hmacUsername,
    );
    return {
      urls: url,
      username: hmacUsername,
      credential: credential,
    } satisfies RTCIceServer;
  } else if (authMethod === "longterm") {
    return {
      urls: turn.url,
      username: username,
      credential: password,
    } satisfies RTCIceServer;
  } else if (authMethod === "cloudflare") {
    const response = await fetch(
      `https://rtc.live.cloudflare.com/v1/turn/keys/${username}/credentials/generate`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${password}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          ttl: 86400,
        }),
      },
    );

    if (!response.ok) {
      throw new Error(
        `parseTurnServer: cloudflare error response: ${response.status}`,
      );
    }

    const iceServers = (await response
      .json()
      .then((data) => data.iceServers)) as RTCIceServer;

    console.log("cloudflare iceServers:", iceServers);
    return iceServers satisfies RTCIceServer;
  } else {
    throw new Error(
      `parseTurnServer: invalid method ${authMethod}`,
    );
  }
}

export async function getIceServers() {
  const servers: RTCIceServer[] = [];
  for (const stun of appOptions.servers.stuns) {
    if (stun.trim().length === 0) continue;
    servers.push({
      urls: stun,
    });
  }
  if (appOptions.servers.turns)
    for (const turn of appOptions.servers.turns) {
      const [error, server] = await catchErrorAsync(
        parseTurnServer(turn),
      );
      if (error) {
        console.error(error);
        continue;
      }

      servers.push(server);
    }

  return servers;
}

export const getRandomAvatar = (seed: string) => {
  return `https://api.dicebear.com/9.x/initials/svg?seed=${seed}`;
};

export const getDefaultProfile = () => {
  // 只使用中文的名，不使用姓
  const name = fakerZH_CN.person.firstName();
  
  // 生成9位随机数字作为远程协助ID，确保第一位不为0
  const generateRandomDigits = (length: number) => {
    let result = '';
    // 第一位生成1-9的数字
    result += Math.floor(Math.random() * 9 + 1).toString();
    // 剩余位生成0-9的数字
    for (let i = 1; i < length; i++) {
      result += Math.floor(Math.random() * 10).toString();
    }
    return result;
  };
  
  return {
    roomId: generateRandomDigits(9), // 生成9位随机数字
    name: name,
    clientId: v4(),
    password: null,
    avatar: getRandomAvatar(name),
    autoJoin: false,
    initalJoin: true,
  };
};

export const [clientProfile, setClientProfile] =
  makePersisted(
    createStore<ClientProfile>(getDefaultProfile()),
    {
      name: "profile",
      storage: localStorage,
    },
  );

export const [clients, setClients] = makePersisted(
  createStore<Record<string, Client>>({}),
  {
    storage: localStorage,
    name: "clients",
  },
);
