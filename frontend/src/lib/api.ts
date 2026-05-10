import type { AuthResponse } from "@/lib/types";

export const DEFAULT_BACKEND = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export function browserBackendBase() {
  if (typeof window === "undefined") {
    return DEFAULT_BACKEND;
  }
  return window.localStorage.getItem("syncstream.backendBase") ?? DEFAULT_BACKEND;
}

export function setBrowserBackendBase(value: string) {
  if (typeof window !== "undefined") {
    window.localStorage.setItem("syncstream.backendBase", value);
  }
}

export function wsUrlFromHttp(baseUrl: string) {
  return baseUrl.replace(/^http:/, "ws:").replace(/^https:/, "wss:") + "/ws";
}

export async function apiFetch<T>(
  path: string,
  options: {
    method?: string;
    body?: unknown;
    token?: string | null;
    backendBase?: string;
  } = {},
): Promise<T> {
  const response = await fetch(`${options.backendBase ?? browserBackendBase()}${path}`, {
    method: options.method ?? "GET",
    credentials: "include",
    headers: {
      ...(options.body === undefined ? {} : { "Content-Type": "application/json" }),
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {}),
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (!response.ok) {
    let message = response.statusText;
    try {
      const error = (await response.json()) as { message?: string };
      message = error.message ?? message;
    } catch {
      // Keep status text when the backend returns no JSON body.
    }
    throw new Error(message);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  return text ? (JSON.parse(text) as T) : (undefined as T);
}

export async function refreshAccessToken(backendBase: string): Promise<AuthResponse> {
  return apiFetch<AuthResponse>("/api/auth/refresh", { method: "POST", backendBase });
}
