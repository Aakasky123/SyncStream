export type JsonValue =
  | string
  | number
  | boolean
  | null
  | JsonValue[]
  | { [key: string]: JsonValue };

export type User = {
  id: string;
  name: string;
  email: string;
  createdAt: string;
};

export type AuthResponse = {
  user: User;
  accessToken: string;
};

export type Workspace = {
  id: string;
  name: string;
  ownerId: string;
  role: string;
  createdAt: string;
  updatedAt: string;
};

export type Member = {
  id: string;
  userId: string | null;
  name: string | null;
  email: string;
  role: string;
  status: string;
  joinedAt: string | null;
};

export type Invitation = {
  id: string;
  workspaceId: string;
  email: string;
  role: string;
  status: string;
  invitationToken: string;
  acceptUrl: string;
  invitationExpiresAt: string;
};

export type DocumentSummary = {
  id: string;
  workspaceId: string;
  title: string;
  version: number;
  createdAt: string;
  updatedAt: string;
};

export type SyncDocument = DocumentSummary & {
  content: JsonValue;
  contentHash: string;
  lastSavedHash: string;
  createdBy: string;
  updatedBy: string;
};

export type DocumentSavedPayload = {
  documentId: string;
  workspaceId: string;
  version: number;
  content: JsonValue;
  steps: JsonValue | null;
  contentHash: string;
  updatedBy: string;
  clientId: string | null;
  clientSeq: number | null;
  instanceId: string;
};

export type ConflictPayload = {
  type: "document:conflict";
  documentId: string;
  serverVersion: number;
  serverContent: JsonValue;
  clientVersion: number;
  clientContent: JsonValue;
};

export type PresenceState = {
  documentId: string;
  userId: string;
  name: string;
  avatarColor: string;
  cursorX: number;
  cursorY: number;
  isTyping: boolean;
  lastSeen: string;
  connectionId: string;
};

export type CommentItem = {
  id: string;
  documentId: string;
  authorId: string;
  authorName: string;
  parentCommentId: string | null;
  selectedText: string | null;
  documentOffsetStart: number | null;
  documentOffsetEnd: number | null;
  content: string;
  resolved: boolean;
  createdAt: string;
  updatedAt: string;
};

export type VersionItem = {
  id: string;
  documentId: string;
  versionNumber: number;
  content: JsonValue;
  contentHash: string;
  createdBy: string;
  createdAt: string;
};

export type NotificationItem = {
  id: string;
  userId: string;
  type: string;
  title: string;
  message: string;
  read: boolean;
  metadataJson: string;
  createdAt: string;
};

export type ActivityItem = {
  id: string;
  workspaceId: string;
  documentId: string | null;
  actorId: string | null;
  actorName: string | null;
  action: string;
  metadataJson: string;
  createdAt: string;
};

export type RealtimeEvent<T = JsonValue> = {
  type: string;
  payload: T;
};
