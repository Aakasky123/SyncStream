import { create } from "zustand";
import { browserBackendBase, setBrowserBackendBase } from "@/lib/api";
import type { User } from "@/lib/types";

type AppState = {
  accessToken: string | null;
  user: User | null;
  backendBase: string;
  selectedWorkspaceId: string | null;
  selectedDocumentId: string | null;
  setSession: (accessToken: string | null, user: User | null) => void;
  setBackendBase: (backendBase: string) => void;
  setSelectedWorkspaceId: (workspaceId: string | null) => void;
  setSelectedDocumentId: (documentId: string | null) => void;
};

export const useAppStore = create<AppState>((set) => ({
  accessToken: null,
  user: null,
  backendBase: browserBackendBase(),
  selectedWorkspaceId: null,
  selectedDocumentId: null,
  setSession: (accessToken, user) => set({ accessToken, user }),
  setBackendBase: (backendBase) => {
    setBrowserBackendBase(backendBase);
    set({ backendBase });
  },
  setSelectedWorkspaceId: (selectedWorkspaceId) => set({ selectedWorkspaceId, selectedDocumentId: null }),
  setSelectedDocumentId: (selectedDocumentId) => set({ selectedDocumentId }),
}));
