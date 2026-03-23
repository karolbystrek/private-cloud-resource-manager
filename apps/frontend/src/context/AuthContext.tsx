"use client";

import {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from "react";

export interface MockUser {
  id: string;
  username: string;
  email: string;
  role: "STUDENT" | "RESEARCHER" | "ADMIN";
}

interface AuthContextType {
  user: MockUser | null;
  isLoading: boolean;
  login: (username: string) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const STORAGE_KEY = "pcrm_user";

/**
 * Mock users — simulates what would come from the backend.
 * In a future milestone this will be replaced with real JWT auth.
 */
const MOCK_USERS: Record<string, MockUser> = {
  admin: {
    id: "00000000-0000-0000-0000-000000000001",
    username: "admin",
    email: "admin@pcrm.local",
    role: "ADMIN",
  },
  student: {
    id: "00000000-0000-0000-0000-000000000002",
    username: "student",
    email: "student@pcrm.local",
    role: "STUDENT",
  },
  researcher: {
    id: "00000000-0000-0000-0000-000000000003",
    username: "researcher",
    email: "researcher@pcrm.local",
    role: "RESEARCHER",
  },
};

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MockUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Restore session from localStorage on mount
  useEffect(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored) {
        setUser(JSON.parse(stored));
      }
    } catch {
      localStorage.removeItem(STORAGE_KEY);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const login = useCallback((username: string) => {
    const normalizedUsername = username.toLowerCase().trim();
    // Accept any username — use mock data if available, otherwise create a student user
    const mockUser = MOCK_USERS[normalizedUsername] ?? {
      id: crypto.randomUUID(),
      username: normalizedUsername,
      email: `${normalizedUsername}@pcrm.local`,
      role: "STUDENT" as const,
    };
    setUser(mockUser);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(mockUser));
  }, []);

  const logout = useCallback(() => {
    setUser(null);
    localStorage.removeItem(STORAGE_KEY);
  }, []);

  return (
    <AuthContext.Provider value={{ user, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
