"use client";

import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Client } from "@stomp/stompjs";
import { EditorContent, useEditor } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import type { JSONContent } from "@tiptap/core";
import {
  Activity,
  Bell,
  Check,
  ChevronRight,
  Circle,
  Clock,
  Copy,
  FilePlus,
  GitBranch,
  LogOut,
  MessageSquare,
  Plus,
  Radio,
  RefreshCw,
  Send,
  Users,
} from "lucide-react";
import { apiFetch, refreshAccessToken, wsUrlFromHttp } from "@/lib/api";
import { sha256 } from "@/lib/hash";
import { useAppStore } from "@/lib/store";
import type {
  ActivityItem,
  AuthResponse,
  CommentItem,
  ConflictPayload,
  DocumentSavedPayload,
  DocumentSummary,
  Invitation,
  JsonValue,
  Member,
  NotificationItem,
  PresenceState,
  RealtimeEvent,
  SyncDocument,
  VersionItem,
  Workspace,
} from "@/lib/types";

const backendOptions = ["http://localhost:8080", "http://localhost:8081"];

export function SyncStreamApp() {
  const queryClient = useQueryClient();
  const {
    accessToken,
    user,
    backendBase,
    selectedWorkspaceId,
    selectedDocumentId,
    setSession,
    setBackendBase,
    setSelectedWorkspaceId,
    setSelectedDocumentId,
  } = useAppStore();
  const [authMode, setAuthMode] = useState<"login" | "register">("register");
  const [authForm, setAuthForm] = useState({ name: "Aakash", email: "aakash@example.com", password: "password123" });
  const [workspaceName, setWorkspaceName] = useState("Demo Workspace");
  const [documentTitle, setDocumentTitle] = useState("Launch Notes");
  const [inviteEmail, setInviteEmail] = useState("teammate@example.com");
  const [lastInvite, setLastInvite] = useState<Invitation | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    refreshAccessToken(backendBase)
      .then((session) => setSession(session.accessToken, session.user))
      .catch(() => undefined);
  }, [backendBase, setSession]);

  useEffect(() => {
    const token = new URLSearchParams(window.location.search).get("invite");
    if (token && accessToken) {
      apiFetch(`/api/workspaces/invitations/${token}/accept`, { method: "POST", token: accessToken, backendBase })
        .then(() => {
          window.history.replaceState({}, "", "/");
          return queryClient.invalidateQueries({ queryKey: ["workspaces"] });
        })
        .catch((err: Error) => setError(err.message));
    }
  }, [accessToken, backendBase, queryClient]);

  const workspaces = useQuery({
    queryKey: ["workspaces", backendBase, accessToken],
    enabled: Boolean(accessToken),
    queryFn: () => apiFetch<Workspace[]>("/api/workspaces", { token: accessToken, backendBase }),
  });

  const documents = useQuery({
    queryKey: ["documents", selectedWorkspaceId, backendBase, accessToken],
    enabled: Boolean(accessToken && selectedWorkspaceId),
    queryFn: () =>
      apiFetch<DocumentSummary[]>(`/api/workspaces/${selectedWorkspaceId}/documents`, { token: accessToken, backendBase }),
  });

  const members = useQuery({
    queryKey: ["members", selectedWorkspaceId, backendBase, accessToken],
    enabled: Boolean(accessToken && selectedWorkspaceId),
    queryFn: () =>
      apiFetch<Member[]>(`/api/workspaces/${selectedWorkspaceId}/members`, { token: accessToken, backendBase }),
  });

  const notifications = useQuery({
    queryKey: ["notifications", backendBase, accessToken],
    enabled: Boolean(accessToken),
    queryFn: () => apiFetch<NotificationItem[]>("/api/notifications", { token: accessToken, backendBase }),
  });

  const activity = useQuery({
    queryKey: ["activity", selectedWorkspaceId, backendBase, accessToken],
    enabled: Boolean(accessToken && selectedWorkspaceId),
    queryFn: () =>
      apiFetch<ActivityItem[]>(`/api/workspaces/${selectedWorkspaceId}/activity`, { token: accessToken, backendBase }),
  });

  const createWorkspace = useMutation({
    mutationFn: () =>
      apiFetch<Workspace>("/api/workspaces", {
        method: "POST",
        body: { name: workspaceName },
        token: accessToken,
        backendBase,
      }),
    onSuccess: (workspace) => {
      setSelectedWorkspaceId(workspace.id);
      queryClient.invalidateQueries({ queryKey: ["workspaces"] });
    },
    onError: (err: Error) => setError(err.message),
  });

  const createDocument = useMutation({
    mutationFn: () =>
      apiFetch<SyncDocument>(`/api/workspaces/${selectedWorkspaceId}/documents`, {
        method: "POST",
        body: { title: documentTitle },
        token: accessToken,
        backendBase,
      }),
    onSuccess: (document) => {
      setSelectedDocumentId(document.id);
      queryClient.invalidateQueries({ queryKey: ["documents", selectedWorkspaceId] });
    },
    onError: (err: Error) => setError(err.message),
  });

  const invite = useMutation({
    mutationFn: () =>
      apiFetch<Invitation>(`/api/workspaces/${selectedWorkspaceId}/invite`, {
        method: "POST",
        body: { email: inviteEmail, role: "MEMBER" },
        token: accessToken,
        backendBase,
      }),
    onSuccess: (invitation) => {
      setLastInvite(invitation);
      queryClient.invalidateQueries({ queryKey: ["members", selectedWorkspaceId] });
    },
    onError: (err: Error) => setError(err.message),
  });

  const submitAuth = async () => {
    setError(null);
    try {
      const path = authMode === "register" ? "/api/auth/register" : "/api/auth/login";
      const body =
        authMode === "register"
          ? authForm
          : {
              email: authForm.email,
              password: authForm.password,
            };
      const session = await apiFetch<AuthResponse>(path, { method: "POST", body, backendBase });
      setSession(session.accessToken, session.user);
      queryClient.clear();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Authentication failed");
    }
  };

  const logout = async () => {
    await apiFetch("/api/auth/logout", { method: "POST", token: accessToken, backendBase }).catch(() => undefined);
    setSession(null, null);
    setSelectedWorkspaceId(null);
    queryClient.clear();
  };

  const copyInviteLink = async (inviteUrl: string) => {
    await navigator.clipboard.writeText(inviteUrl);
    setToast("Invite link copied");
    window.setTimeout(() => setToast(null), 2200);
  };

  const selectedWorkspace = useMemo(
    () => workspaces.data?.find((workspace) => workspace.id === selectedWorkspaceId) ?? null,
    [selectedWorkspaceId, workspaces.data],
  );

  return (
    <main className="min-h-screen bg-[#f8fafc] text-[#172033]">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-[1500px] flex-wrap items-center justify-between gap-3 px-5 py-3">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-md bg-emerald-600 text-white">
              <Radio size={20} />
            </div>
            <div>
              <h1 className="text-lg font-semibold">SyncStream</h1>
              <p className="text-xs text-slate-500">Distributed WebSocket collaboration demo</p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <select
              className="h-9 rounded-md border border-slate-300 bg-white px-2 text-sm"
              value={backendBase}
              onChange={(event) => setBackendBase(event.target.value)}
              title="Backend instance"
            >
              {backendOptions.map((option) => (
                <option key={option} value={option}>
                  {option.replace("http://localhost:", "backend ")}
                </option>
              ))}
            </select>
            {user ? (
              <>
                <span className="rounded-md bg-slate-100 px-3 py-2 text-sm">{user.name}</span>
                <IconButton label="Logout" onClick={logout}>
                  <LogOut size={16} />
                </IconButton>
              </>
            ) : null}
          </div>
        </div>
      </header>

      <div className="mx-auto grid max-w-[1500px] grid-cols-1 gap-4 px-5 py-4 xl:grid-cols-[320px_1fr_360px]">
        <aside className="space-y-4">
          {!user ? (
            <section className="rounded-md border border-slate-200 bg-white p-4">
              <div className="mb-3 flex rounded-md bg-slate-100 p-1">
                <button
                  className={`h-9 flex-1 rounded px-3 text-sm ${authMode === "register" ? "bg-white shadow-sm" : ""}`}
                  onClick={() => setAuthMode("register")}
                >
                  Register
                </button>
                <button
                  className={`h-9 flex-1 rounded px-3 text-sm ${authMode === "login" ? "bg-white shadow-sm" : ""}`}
                  onClick={() => setAuthMode("login")}
                >
                  Login
                </button>
              </div>
              {authMode === "register" ? (
                <TextInput
                  label="Name"
                  value={authForm.name}
                  onChange={(value) => setAuthForm((current) => ({ ...current, name: value }))}
                />
              ) : null}
              <TextInput
                label="Email"
                value={authForm.email}
                onChange={(value) => setAuthForm((current) => ({ ...current, email: value }))}
              />
              <TextInput
                label="Password"
                type="password"
                value={authForm.password}
                onChange={(value) => setAuthForm((current) => ({ ...current, password: value }))}
              />
              <button className="mt-3 h-10 w-full rounded-md bg-emerald-600 px-3 text-sm font-medium text-white" onClick={submitAuth}>
                {authMode === "register" ? "Create account" : "Sign in"}
              </button>
            </section>
          ) : (
            <>
              <section className="rounded-md border border-slate-200 bg-white p-4">
                <PanelTitle icon={<Users size={16} />} title="Workspaces" />
                <div className="mt-3 flex gap-2">
                  <input
                    className="h-9 min-w-0 flex-1 rounded-md border border-slate-300 px-2 text-sm"
                    value={workspaceName}
                    onChange={(event) => setWorkspaceName(event.target.value)}
                  />
                  <IconButton label="Create workspace" onClick={() => createWorkspace.mutate()}>
                    <Plus size={16} />
                  </IconButton>
                </div>
                <div className="mt-3 space-y-2">
                  {workspaces.data?.map((workspace) => (
                    <button
                      key={workspace.id}
                      className={`flex w-full items-center justify-between rounded-md border px-3 py-2 text-left text-sm ${
                        workspace.id === selectedWorkspaceId ? "border-emerald-500 bg-emerald-50" : "border-slate-200 bg-white"
                      }`}
                      onClick={() => setSelectedWorkspaceId(workspace.id)}
                    >
                      <span>{workspace.name}</span>
                      <ChevronRight size={15} />
                    </button>
                  ))}
                </div>
              </section>

              <section className="rounded-md border border-slate-200 bg-white p-4">
                <PanelTitle icon={<FilePlus size={16} />} title="Documents" />
                <div className="mt-3 flex gap-2">
                  <input
                    className="h-9 min-w-0 flex-1 rounded-md border border-slate-300 px-2 text-sm"
                    value={documentTitle}
                    onChange={(event) => setDocumentTitle(event.target.value)}
                    disabled={!selectedWorkspaceId}
                  />
                  <IconButton label="Create document" disabled={!selectedWorkspaceId} onClick={() => createDocument.mutate()}>
                    <Plus size={16} />
                  </IconButton>
                </div>
                <div className="mt-3 space-y-2">
                  {documents.data?.map((document) => (
                    <button
                      key={document.id}
                      className={`w-full rounded-md border px-3 py-2 text-left text-sm ${
                        document.id === selectedDocumentId ? "border-sky-500 bg-sky-50" : "border-slate-200 bg-white"
                      }`}
                      onClick={() => setSelectedDocumentId(document.id)}
                    >
                      <div className="font-medium">{document.title}</div>
                      <div className="text-xs text-slate-500">v{document.version}</div>
                    </button>
                  ))}
                </div>
              </section>
            </>
          )}

          {error ? (
            <div className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">{error}</div>
          ) : null}
        </aside>

        <section className="min-w-0">
          {accessToken && selectedDocumentId ? (
            <EditorPanel
              key={selectedDocumentId}
              accessToken={accessToken}
              backendBase={backendBase}
              documentId={selectedDocumentId}
              user={user}
              workspaceId={selectedWorkspaceId}
            />
          ) : (
            <div className="flex min-h-[640px] items-center justify-center rounded-md border border-dashed border-slate-300 bg-white text-sm text-slate-500">
              Register or log in, create a workspace, then open a document.
            </div>
          )}
        </section>

        <aside className="space-y-4">
          <section className="rounded-md border border-slate-200 bg-white p-4">
            <PanelTitle icon={<Users size={16} />} title={selectedWorkspace?.name ?? "Members"} />
            <div className="mt-3 flex gap-2">
              <input
                className="h-9 min-w-0 flex-1 rounded-md border border-slate-300 px-2 text-sm"
                value={inviteEmail}
                onChange={(event) => setInviteEmail(event.target.value)}
                disabled={!selectedWorkspaceId}
              />
              <IconButton label="Invite" disabled={!selectedWorkspaceId} onClick={() => invite.mutate()}>
                <Send size={16} />
              </IconButton>
            </div>
            {lastInvite ? (
              <div className="mt-3 flex items-stretch gap-2">
                <div
                  className="min-w-0 flex-1 cursor-pointer rounded-md border border-emerald-200 bg-emerald-50 px-3 py-2 text-xs text-emerald-800 hover:border-emerald-300 hover:bg-emerald-100"
                  role="button"
                  tabIndex={0}
                  title="Copy invite link"
                  onClick={() => copyInviteLink(lastInvite.acceptUrl)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      copyInviteLink(lastInvite.acceptUrl);
                    }
                  }}
                >
                  <span className="block truncate">{lastInvite.acceptUrl}</span>
                </div>
                <IconButton label="Copy invite link" onClick={() => copyInviteLink(lastInvite.acceptUrl)}>
                  <Copy size={16} />
                </IconButton>
              </div>
            ) : null}
            <div className="mt-3 space-y-2">
              {members.data?.map((member) => (
                <div key={member.id} className="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2 text-sm">
                  <div>
                    <div className="font-medium">{member.name ?? member.email}</div>
                    <div className="text-xs text-slate-500">{member.status}</div>
                  </div>
                  <span className="rounded bg-white px-2 py-1 text-xs">{member.role}</span>
                </div>
              ))}
            </div>
          </section>

          <section className="rounded-md border border-slate-200 bg-white p-4">
            <PanelTitle icon={<Bell size={16} />} title="Notifications" />
            <div className="mt-3 max-h-52 space-y-2 overflow-auto">
              {notifications.data?.map((notification) => (
                <div key={notification.id} className="rounded-md bg-slate-50 px-3 py-2 text-sm">
                  <div className="font-medium">{notification.title}</div>
                  <div className="text-xs text-slate-500">{notification.message}</div>
                </div>
              ))}
            </div>
          </section>

          <section className="rounded-md border border-slate-200 bg-white p-4">
            <PanelTitle icon={<Activity size={16} />} title="Activity" />
            <div className="mt-3 max-h-64 space-y-2 overflow-auto">
              {activity.data?.map((item) => (
                <div key={item.id} className="rounded-md bg-slate-50 px-3 py-2 text-xs">
                  <div className="font-medium">{item.action}</div>
                  <div className="text-slate-500">{new Date(item.createdAt).toLocaleTimeString()}</div>
                </div>
              ))}
            </div>
          </section>
        </aside>
      </div>
      {toast ? (
        <div className="fixed bottom-5 left-1/2 z-30 -translate-x-1/2 rounded-md bg-slate-900 px-4 py-2 text-sm text-white shadow-lg">
          {toast}
        </div>
      ) : null}
    </main>
  );
}

function EditorPanel({
  accessToken,
  backendBase,
  documentId,
  user,
  workspaceId,
}: {
  accessToken: string;
  backendBase: string;
  documentId: string;
  user: { id: string; name: string } | null;
  workspaceId: string | null;
}) {
  const queryClient = useQueryClient();
  const clientId = useRef(crypto.randomUUID());
  const stompRef = useRef<Client | null>(null);
  const debounceRef = useRef<number | null>(null);
  const applyingRemote = useRef(false);
  const [socketState, setSocketState] = useState("connecting");
  const [saveState, setSaveState] = useState("Idle");
  const [version, setVersion] = useState(0);
  const [presence, setPresence] = useState<PresenceState[]>([]);
  const [conflict, setConflict] = useState<ConflictPayload | null>(null);
  const [mergeJson, setMergeJson] = useState("");
  const [commentText, setCommentText] = useState("Looks good. Can we tighten this paragraph?");
  const [selectedText, setSelectedText] = useState("");

  const documentQuery = useQuery({
    queryKey: ["document", documentId, backendBase, accessToken],
    queryFn: () => apiFetch<SyncDocument>(`/api/documents/${documentId}`, { token: accessToken, backendBase }),
  });
  const currentVersion = version || documentQuery.data?.version || 0;

  const comments = useQuery({
    queryKey: ["comments", documentId, backendBase, accessToken],
    queryFn: () => apiFetch<CommentItem[]>(`/api/documents/${documentId}/comments`, { token: accessToken, backendBase }),
  });

  const versions = useQuery({
    queryKey: ["versions", documentId, backendBase, accessToken],
    queryFn: () => apiFetch<VersionItem[]>(`/api/documents/${documentId}/versions`, { token: accessToken, backendBase }),
  });

  const editor = useEditor({
    extensions: [StarterKit],
    content: documentQuery.data?.content as JSONContent | undefined,
    immediatelyRender: false,
    onUpdate: ({ editor: activeEditor, transaction }) => {
      if (applyingRemote.current || !documentQuery.data) {
        return;
      }
      setSaveState("Queued");
      if (debounceRef.current) {
        window.clearTimeout(debounceRef.current);
      }
      const steps = transaction.steps.map((step) => step.toJSON()) as JsonValue;
      debounceRef.current = window.setTimeout(() => {
        sendPatch(activeEditor.getJSON(), steps);
      }, 800);
    },
  });

  const loadedDocument = documentQuery.data;

  useEffect(() => {
    if (!loadedDocument || !editor) {
      return;
    }
    applyingRemote.current = true;
    editor.commands.setContent(loadedDocument.content as JSONContent);
    applyingRemote.current = false;
  }, [loadedDocument, editor]);

  useEffect(() => {
    if (!accessToken || !documentId || !documentQuery.data) {
      return;
    }
    const client = new Client({
      brokerURL: wsUrlFromHttp(backendBase),
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      reconnectDelay: 2000,
      debug: () => undefined,
      onConnect: () => {
        setSocketState("connected");
        client.subscribe(`/topic/documents/${documentId}`, (message) => {
          const event = JSON.parse(message.body) as RealtimeEvent<unknown>;
          if (event.type === "document:saved") {
            const payload = event.payload as DocumentSavedPayload;
            setVersion(payload.version);
            setSaveState(payload.clientId === clientId.current ? "Saved" : `Remote save from ${payload.instanceId}`);
            if (payload.clientId !== clientId.current && editor) {
              applyingRemote.current = true;
              editor.commands.setContent(payload.content as JSONContent);
              applyingRemote.current = false;
            }
            queryClient.invalidateQueries({ queryKey: ["documents", workspaceId] });
            queryClient.invalidateQueries({ queryKey: ["document", documentId] });
          }
          if (event.type === "presence:heartbeat") {
            const payload = event.payload as PresenceState;
            setPresence((current) => [payload, ...current.filter((item) => item.userId !== payload.userId)]);
          }
          if (event.type === "presence:leave") {
            const payload = event.payload as { userId: string };
            setPresence((current) => current.filter((item) => item.userId !== payload.userId));
          }
          if (event.type === "comment:new") {
            queryClient.invalidateQueries({ queryKey: ["comments", documentId] });
            queryClient.invalidateQueries({ queryKey: ["notifications"] });
          }
        });
        client.subscribe(`/user/queue/documents/${documentId}`, (message) => {
          const event = JSON.parse(message.body) as RealtimeEvent<ConflictPayload>;
          if (event.type === "document:conflict") {
            setConflict(event.payload);
            setMergeJson(JSON.stringify(event.payload.clientContent, null, 2));
            setSaveState("Conflict");
          }
        });
        client.subscribe("/user/queue/notifications", () => {
          queryClient.invalidateQueries({ queryKey: ["notifications"] });
        });
        client.publish({ destination: `/app/documents/${documentId}/join`, body: "{}" });
      },
      onWebSocketClose: () => setSocketState("disconnected"),
      onStompError: () => setSocketState("error"),
    });
    stompRef.current = client;
    client.activate();
    return () => {
      client.publish({ destination: `/app/documents/${documentId}/leave`, body: "{}" });
      client.deactivate();
      stompRef.current = null;
    };
  }, [accessToken, backendBase, documentId, documentQuery.data, editor, queryClient, workspaceId]);

  useEffect(() => {
    if (!documentId || !user || !stompRef.current) {
      return;
    }
    const heartbeat = window.setInterval(() => {
      const client = stompRef.current;
      if (!client?.connected) {
        return;
      }
      client.publish({
        destination: "/app/presence/heartbeat",
        body: JSON.stringify({
          documentId,
          name: user.name,
          avatarColor: avatarColor(user.id),
          cursorX: Math.floor(Math.random() * 520),
          cursorY: Math.floor(Math.random() * 280),
          isTyping: saveState === "Queued" || saveState === "Saving...",
          connectionId: clientId.current,
        }),
      });
    }, 5000);
    return () => window.clearInterval(heartbeat);
  }, [documentId, saveState, user]);

  const sendPatch = async (content: JSONContent | JsonValue, steps: JsonValue | null, forcedBaseVersion?: number) => {
    const client = stompRef.current;
    if (!client?.connected) {
      setSaveState("Offline queue");
      return;
    }
    setSaveState("Saving...");
    const canonical = JSON.stringify(content);
    client.publish({
      destination: `/app/documents/${documentId}/patch`,
      body: JSON.stringify({
        baseVersion: forcedBaseVersion ?? currentVersion,
        steps,
        content,
        contentHash: await sha256(canonical),
        clientId: clientId.current,
        clientSeq: Date.now(),
        clientUpdatedAt: new Date().toISOString(),
      }),
    });
  };

  const createComment = useMutation({
    mutationFn: () =>
      apiFetch<CommentItem>(`/api/documents/${documentId}/comments`, {
        method: "POST",
        body: {
          selectedText,
          documentOffsetStart: 0,
          documentOffsetEnd: selectedText.length,
          content: commentText,
        },
        token: accessToken,
        backendBase,
      }),
    onSuccess: () => {
      setCommentText("");
      queryClient.invalidateQueries({ queryKey: ["comments", documentId] });
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
    },
  });

  const createVersion = useMutation({
    mutationFn: () =>
      apiFetch<VersionItem>(`/api/documents/${documentId}/versions`, {
        method: "POST",
        token: accessToken,
        backendBase,
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["versions", documentId] }),
  });

  const restoreVersion = useMutation({
    mutationFn: (versionId: string) =>
      apiFetch<SyncDocument>(`/api/documents/${documentId}/versions/${versionId}/restore`, {
        method: "POST",
        token: accessToken,
        backendBase,
      }),
    onSuccess: (document) => {
      setVersion(document.version);
      editor?.commands.setContent(document.content as JSONContent);
      queryClient.invalidateQueries({ queryKey: ["document", documentId] });
      queryClient.invalidateQueries({ queryKey: ["versions", documentId] });
    },
  });

  const resolveConflictUseServer = () => {
    if (!conflict) {
      return;
    }
    applyingRemote.current = true;
    editor?.commands.setContent(conflict.serverContent as JSONContent);
    applyingRemote.current = false;
    setVersion(conflict.serverVersion);
    setConflict(null);
    setSaveState("Server version applied");
  };

  const resolveConflictKeepMine = () => {
    if (!conflict) {
      return;
    }
    sendPatch(conflict.clientContent, null, conflict.serverVersion);
    setConflict(null);
  };

  const resolveConflictMerge = () => {
    if (!conflict) {
      return;
    }
    try {
      const parsed = JSON.parse(mergeJson) as JsonValue;
      editor?.commands.setContent(parsed as JSONContent);
      sendPatch(parsed, null, conflict.serverVersion);
      setConflict(null);
    } catch {
      setSaveState("Merge JSON is invalid");
    }
  };

  if (documentQuery.isLoading) {
    return <div className="rounded-md border border-slate-200 bg-white p-5 text-sm">Loading document...</div>;
  }

  if (!documentQuery.data) {
    return <div className="rounded-md border border-slate-200 bg-white p-5 text-sm">Document not found.</div>;
  }

  return (
    <div className="space-y-4">
      <section className="rounded-md border border-slate-200 bg-white">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
          <div>
            <h2 className="text-lg font-semibold">{documentQuery.data.title}</h2>
            <div className="mt-1 flex flex-wrap items-center gap-3 text-xs text-slate-500">
              <span>v{currentVersion}</span>
              <span>{saveState}</span>
              <span className="flex items-center gap-1">
                <Circle className={socketState === "connected" ? "fill-emerald-500 text-emerald-500" : "fill-rose-500 text-rose-500"} size={9} />
                {socketState}
              </span>
              <span>{backendBase}</span>
            </div>
          </div>
          <div className="flex items-center gap-2">
            {presence.map((item) => (
              <div
                key={item.userId}
                className="flex h-8 w-8 items-center justify-center rounded-full text-xs font-semibold text-white"
                style={{ backgroundColor: item.avatarColor }}
                title={`${item.name}${item.isTyping ? " is typing" : ""}`}
              >
                {item.name.slice(0, 1).toUpperCase()}
              </div>
            ))}
            <IconButton label="Snapshot version" onClick={() => createVersion.mutate()}>
              <GitBranch size={16} />
            </IconButton>
            <IconButton
              label="Trigger stale conflict"
              onClick={() => editor && sendPatch(editor.getJSON(), null, Math.max(0, currentVersion - 3))}
            >
              <RefreshCw size={16} />
            </IconButton>
          </div>
        </div>
        <div className="border-b border-slate-200 bg-slate-50 px-4 py-2">
          <div className="flex flex-wrap gap-2">
            <ToolbarButton label="Bold" onClick={() => editor?.chain().focus().toggleBold().run()} active={editor?.isActive("bold")}>
              B
            </ToolbarButton>
            <ToolbarButton label="Italic" onClick={() => editor?.chain().focus().toggleItalic().run()} active={editor?.isActive("italic")}>
              I
            </ToolbarButton>
            <ToolbarButton label="Heading" onClick={() => editor?.chain().focus().toggleHeading({ level: 2 }).run()} active={editor?.isActive("heading")}>
              H2
            </ToolbarButton>
            <ToolbarButton label="Bullet list" onClick={() => editor?.chain().focus().toggleBulletList().run()} active={editor?.isActive("bulletList")}>
              List
            </ToolbarButton>
          </div>
        </div>
        <EditorContent editor={editor} className="min-h-[460px]" />
      </section>

      <div className="grid gap-4 lg:grid-cols-2">
        <section className="rounded-md border border-slate-200 bg-white p-4">
          <PanelTitle icon={<MessageSquare size={16} />} title="Comments" />
          <div className="mt-3 grid gap-2 sm:grid-cols-[1fr_1fr_auto]">
            <input
              className="h-9 rounded-md border border-slate-300 px-2 text-sm"
              value={selectedText}
              onChange={(event) => setSelectedText(event.target.value)}
              placeholder="Selected text"
            />
            <input
              className="h-9 rounded-md border border-slate-300 px-2 text-sm"
              value={commentText}
              onChange={(event) => setCommentText(event.target.value)}
              placeholder="Comment"
            />
            <IconButton label="Add comment" onClick={() => createComment.mutate()}>
              <Plus size={16} />
            </IconButton>
          </div>
          <div className="mt-3 max-h-56 space-y-2 overflow-auto">
            {comments.data?.map((comment) => (
              <div key={comment.id} className="rounded-md bg-slate-50 px-3 py-2 text-sm">
                <div className="flex items-center justify-between">
                  <span className="font-medium">{comment.authorName}</span>
                  <span className="text-xs text-slate-500">{new Date(comment.createdAt).toLocaleTimeString()}</span>
                </div>
                {comment.selectedText ? <div className="mt-1 text-xs text-slate-500">&quot;{comment.selectedText}&quot;</div> : null}
                <div className="mt-1">{comment.content}</div>
              </div>
            ))}
          </div>
        </section>

        <section className="rounded-md border border-slate-200 bg-white p-4">
          <PanelTitle icon={<Clock size={16} />} title="Versions" />
          <div className="mt-3 max-h-72 space-y-2 overflow-auto">
            {versions.data?.map((item) => (
              <div key={item.id} className="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2 text-sm">
                <div>
                  <div className="font-medium">Snapshot v{item.versionNumber}</div>
                  <div className="text-xs text-slate-500">{new Date(item.createdAt).toLocaleString()}</div>
                </div>
                <IconButton label="Restore version" onClick={() => restoreVersion.mutate(item.id)}>
                  <Check size={16} />
                </IconButton>
              </div>
            ))}
          </div>
        </section>
      </div>

      {conflict ? (
        <div className="fixed inset-0 z-20 flex items-center justify-center bg-slate-950/40 p-4">
          <div className="w-full max-w-5xl rounded-md bg-white p-4 shadow-xl">
            <div className="mb-3 flex items-center justify-between">
              <h3 className="text-lg font-semibold">Conflict detected</h3>
              <span className="text-sm text-slate-500">
                client v{conflict.clientVersion} / server v{conflict.serverVersion}
              </span>
            </div>
            <div className="grid gap-3 lg:grid-cols-2">
              <JsonPanel title="Your unsaved version" value={conflict.clientContent} />
              <JsonPanel title="Latest server version" value={conflict.serverContent} />
            </div>
            <textarea
              className="mt-3 h-36 w-full rounded-md border border-slate-300 p-2 font-mono text-xs"
              value={mergeJson}
              onChange={(event) => setMergeJson(event.target.value)}
            />
            <div className="mt-3 flex flex-wrap justify-end gap-2">
              <button className="h-9 rounded-md border border-slate-300 px-3 text-sm" onClick={resolveConflictUseServer}>
                Use server version
              </button>
              <button className="h-9 rounded-md border border-slate-300 px-3 text-sm" onClick={resolveConflictKeepMine}>
                Keep mine
              </button>
              <button className="h-9 rounded-md bg-emerald-600 px-3 text-sm font-medium text-white" onClick={resolveConflictMerge}>
                Merge manually
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function avatarColor(seed: string) {
  const colors = ["#0f766e", "#2563eb", "#7c3aed", "#be123c", "#c2410c", "#15803d"];
  const index = seed.split("").reduce((sum, char) => sum + char.charCodeAt(0), 0) % colors.length;
  return colors[index];
}

function TextInput({
  label,
  value,
  type = "text",
  onChange,
}: {
  label: string;
  value: string;
  type?: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="mt-3 block text-sm">
      <span className="mb-1 block text-xs font-medium text-slate-500">{label}</span>
      <input
        className="h-10 w-full rounded-md border border-slate-300 px-3 text-sm"
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
      />
    </label>
  );
}

function PanelTitle({ icon, title }: { icon: ReactNode; title: string }) {
  return (
    <div className="flex items-center gap-2 text-sm font-semibold">
      {icon}
      <span>{title}</span>
    </div>
  );
}

function IconButton({
  label,
  children,
  onClick,
  disabled,
}: {
  label: string;
  children: ReactNode;
  onClick?: () => void;
  disabled?: boolean;
}) {
  return (
    <button
      className="flex h-9 w-9 items-center justify-center rounded-md border border-slate-300 bg-white text-slate-700 disabled:cursor-not-allowed disabled:opacity-40"
      title={label}
      onClick={onClick}
      disabled={disabled}
      type="button"
    >
      {children}
    </button>
  );
}

function ToolbarButton({
  label,
  children,
  onClick,
  active,
}: {
  label: string;
  children: ReactNode;
  onClick: () => void;
  active?: boolean;
}) {
  return (
    <button
      className={`h-8 min-w-9 rounded-md border px-2 text-xs font-medium ${
        active ? "border-emerald-500 bg-emerald-50 text-emerald-700" : "border-slate-300 bg-white text-slate-700"
      }`}
      title={label}
      type="button"
      onClick={onClick}
    >
      {children}
    </button>
  );
}

function JsonPanel({ title, value }: { title: string; value: JsonValue }) {
  return (
    <div className="rounded-md border border-slate-200">
      <div className="border-b border-slate-200 bg-slate-50 px-3 py-2 text-sm font-medium">{title}</div>
      <pre className="max-h-72 overflow-auto p-3 text-xs">{JSON.stringify(value, null, 2)}</pre>
    </div>
  );
}
