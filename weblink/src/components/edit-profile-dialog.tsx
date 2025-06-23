import {
  clientProfile,
  setClientProfile,
  getRandomAvatar,
} from "@/libs/core/store";
import { createDialog } from "./dialogs/dialog";
import { Input } from "./ui/input";
import { Button } from "./ui/button";
import { optional } from "@/libs/core/utils/optional";
import {
  ComponentProps,
  createMemo,
  Show,
  splitProps,
} from "solid-js";
import {
  Avatar,
  AvatarFallback,
  AvatarImage,
} from "./ui/avatar";
import { toast } from "solid-sonner";
import { t } from "@/i18n";
import { getInitials } from "@/libs/utils/name";

// 图片文件转换为正方形头像
const imageFileToFilledSquareAvatar = async (
  file: File,
  size: number,
): Promise<Blob> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const img = new Image();
      img.onload = () => {
        const canvas = document.createElement("canvas");
        canvas.width = size;
        canvas.height = size;
        const ctx = canvas.getContext("2d");
        if (!ctx) {
          reject(new Error("Failed to get canvas context"));
          return;
        }
        
        // 计算裁剪区域，保持图像比例
        let sx = 0, sy = 0, sWidth = img.width, sHeight = img.height;
        if (img.width > img.height) {
          sx = (img.width - img.height) / 2;
          sWidth = img.height;
        } else if (img.height > img.width) {
          sy = (img.height - img.width) / 2;
          sHeight = img.width;
        }
        
        // 绘制到画布上
        ctx.drawImage(img, sx, sy, sWidth, sHeight, 0, 0, size, size);
        
        // 转换为Blob
        canvas.toBlob((blob) => {
          if (blob) {
            resolve(blob);
          } else {
            reject(new Error("Failed to create blob"));
          }
        }, "image/png");
      };
      img.onerror = () => reject(new Error("Failed to load image"));
      img.src = e.target?.result as string;
    };
    reader.onerror = () => reject(new Error("Failed to read file"));
    reader.readAsDataURL(file);
  });
};

export const createEditProfileDialog = () => {
  const { open, close, submit, Component } = createDialog({
    title: () => t("client.index.edit_profile"),
    content: () => (
      <>
        <form
          id="edit-profile"
          class="grid gap-4 overflow-y-auto p-1"
          onSubmit={(ev) => {
            ev.preventDefault();
            submit(clientProfile);
            toast.success(t("common.notification.profile_updated"));
          }}
        >
          <label class="flex flex-col gap-2">
            <span class="input-label">
              {t("common.join_form.name")}
            </span>
            <Input
              required
              value={clientProfile.name}
              onInput={(ev) =>
                setClientProfile(
                  "name",
                  ev.currentTarget.value,
                )
              }
            />
          </label>
          <label class="flex flex-col gap-2">
            <span class="input-label">
              {t("common.join_form.avatar_url")}
            </span>
            <Input
              placeholder="Enter a link or upload an image"
              type="url"
              value={clientProfile.avatar ?? ""}
              onInput={(ev) =>
                setClientProfile(
                  "avatar",
                  optional(ev.currentTarget.value),
                )
              }
            />
            <div class="flex items-center gap-2">
              <Input
                type="file"
                multiple={false}
                accept="image/*"
                onChange={async (ev) => {
                  const file =
                    ev.currentTarget.files?.item(0);
                  if (!file) return;

                  try {
                    const blob = await imageFileToFilledSquareAvatar(file, 128);
                    const url = URL.createObjectURL(blob);
                    setClientProfile("avatar", url);
                  } catch (error) {
                    console.error("Failed to process image:", error);
                    toast.error(t("common.notification.image_processing_failed"));
                  }
                }}
              />
              <Avatar>
                <AvatarImage
                  src={clientProfile.avatar ?? undefined}
                />
                <AvatarFallback>
                  {getInitials(clientProfile.name)}
                </AvatarFallback>
              </Avatar>
            </div>
          </label>
        </form>
      </>
    ),
    confirm: (
      <Button type="submit" form="edit-profile">
        {t("common.action.confirm")}
      </Button>
    ),
    cancel: (
      <Button variant="outline" onClick={() => close()}>
        {t("common.action.cancel")}
      </Button>
    ),
  });
  return { open, Component };
}; 