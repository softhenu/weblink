import {
  createEffect,
  createMemo,
  createSignal,
  For,
  Match,
  Show,
  Switch,
} from "solid-js";
import { Button } from "./ui/button";
import { ClientID, ClientInfo } from "@/libs/core/type";
import {
  Avatar,
  AvatarFallback,
  AvatarImage,
} from "@/components/ui/avatar";
import { getInitials } from "@/libs/utils/name";
import { sessionService } from "@/libs/services/session-service";
import {
  CommandDialog,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "./ui/command";
import {
  Checkbox,
  CheckboxControl,
} from "@/components/ui/checkbox";
import {
  IconForward,
  IconDraft,
} from "./icons";
import { useWebRTC } from "@/libs/core/rtc-context";
import { toast } from "solid-sonner";
import { FileMetaData } from "@/libs/cache";
import { t } from "@/i18n";

const ForwardClientItem = (props: {
  checked: boolean;
  client: ClientInfo;
  onToggle?: (clientId: string, checked: boolean) => void;
}) => {
  return (
    <CommandItem
      class="flex items-center gap-2"
      onSelect={() => {
        console.log("select", props.client.clientId);
        props.onToggle?.(
          props.client.clientId,
          !props.checked,
        );
      }}
    >
      <Avatar>
        <AvatarImage
          src={props.client.avatar ?? undefined}
        />
        <AvatarFallback>
          {getInitials(props.client.name)}
        </AvatarFallback>
      </Avatar>
      <p>{props.client.name}</p>
      <div class="ml-auto">
        <Checkbox
          role="checkbox"
          checked={props.checked}
          aria-label="Select client"
        >
          <CheckboxControl />
        </Checkbox>
      </div>
    </CommandItem>
  );
};

type ShareDataType =
  | {
      type: "text";
      data: string;
    }
  | {
      type: "cache";
      data: FileMetaData;
    }
  | {
      type: "file";
      data: File;
    };

export const createForwardDialog = () => {
  const forwardTarget = (shareData: ShareData) => {
    const shares: ShareDataType[] = [];
    if (shareData.text)
      shares.push({ type: "text", data: shareData.text });
    if (shareData.files) {
      for (const file of shareData.files) {
        shares.push({ type: "file", data: file });
      }
    }
    setShareData(shares);
    setOpen(true);
  };
  const { sendText, sendFile, shareFile } = useWebRTC();
  const forwardCache = (
    fileData: FileMetaData | FileMetaData[],
  ) => {
    const shares: ShareDataType[] = [];
    if (Array.isArray(fileData)) {
      for (const data of fileData) {
        shares.push({ type: "cache", data });
      }
    } else {
      shares.push({ type: "cache", data: fileData });
    }
    setShareData(shares);
    setOpen(true);
  };
  const [open, setOpen] = createSignal(false);
  const availableClients = createMemo(() =>
    Object.values(sessionService.clientViewData).filter(
      (client) => client.onlineStatus === "online",
    ),
  );
  const [shareData, setShareData] = createSignal<
    ShareDataType[] | null
  >(null);
  const [selectedClients, setSelectedClients] =
    createSignal<ClientID[]>([]);

  return {
    forwardTarget,
    forwardCache,
    Component: () => (
      <CommandDialog open={open()} onOpenChange={setOpen}>
        <CommandInput
          placeholder={t(
            "common.forward_dialog.placeholder",
          )}
        />

        <CommandList>
          <CommandEmpty>
            {t("common.forward_dialog.no_results_found")}
          </CommandEmpty>
          <CommandGroup>
            <For each={availableClients()}>
              {(item) => (
                <ForwardClientItem
                  checked={selectedClients().includes(
                    item.clientId,
                  )}
                  client={item}
                  onToggle={(clientId, checked) => {
                    setSelectedClients(
                      checked
                        ? [...selectedClients(), clientId]
                        : selectedClients().filter(
                            (id) => id !== clientId,
                          ),
                    );
                  }}
                />
              )}
            </For>
          </CommandGroup>
        </CommandList>
        <div class="flex flex-col gap-2 px-4">
          <p class="text-lg font-semibold">
            {t("common.forward_dialog.title")}
          </p>
          <For each={shareData()}>
            {(data) => (
              <Switch>
                <Match when={data.type === "text"}>
                  {
                    <p class="line-clamp-2 text-xs">
                      {data.data as string}
                    </p>
                  }
                </Match>
                <Match when={data.type === "cache"}>
                  <div class="flex items-center gap-1">
                    <IconDraft class="inline size-4" />
                    <span class="text-sm">
                      {(data.data as FileMetaData).fileName}
                    </span>
                  </div>
                </Match>
                <Match when={data.type === "file"}>
                  <div class="flex items-center gap-1">
                    <IconDraft class="inline size-4" />
                    <span class="text-sm">
                      {(data.data as File).name}
                    </span>
                  </div>
                </Match>
              </Switch>
            )}
          </For>
        </div>
        <div
          class="flex flex-col-reverse justify-normal gap-2 p-4 sm:flex-row
            sm:justify-end"
        >
          <Button
            variant="outline"
            onClick={() => {
              setSelectedClients([]);
              setOpen(false);
            }}
          >
            {t("common.action.cancel")}
          </Button>

          <Button
            disabled={selectedClients().length === 0}
            onClick={() => {
              const data = shareData();
              for (const clientId of selectedClients()) {
                for (const item of data ?? []) {
                  try {
                    if (item.type === "text") {
                      sendText(
                        item.data as string,
                        clientId,
                      );
                    } else if (item.type === "file") {
                      sendFile(item.data as File, clientId);
                    } else if (item.type === "cache") {
                      shareFile(
                        (item.data as FileMetaData).id,
                        clientId,
                      );
                    }
                  } catch (err) {
                    if (err instanceof Error) {
                      toast.error(
                        `Failed to share ${item.type} to ${clientId}: ${err.message}`,
                      );
                    } else {
                      toast.error(
                        `Failed to share ${item.type} to ${clientId}`,
                      );
                    }
                  }
                }
              }
              setSelectedClients([]);
              setOpen(false);
            }}
          >
            <IconForward class="mr-2 size-4" />
            {t("common.action.forward")}
          </Button>
        </div>
      </CommandDialog>
    ),
  };
};
