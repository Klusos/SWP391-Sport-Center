import { createContext, useContext } from "react";

const PortalIdentityContext = createContext({ currentUserName: "" });

export function PortalIdentityProvider({ currentUserName, children }) {
  return (
    <PortalIdentityContext.Provider value={{ currentUserName }}>
      {children}
    </PortalIdentityContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function usePortalIdentity() {
  return useContext(PortalIdentityContext);
}
