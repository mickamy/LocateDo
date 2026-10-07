"use strict";

const environments = {
  prod: {
    api: "https://api.locatedo.com",
    googleClientId: "44680780234-mdnjqmlqu23rgas8dhojvoq6pcnoulq8.apps.googleusercontent.com",
  },
  stg: {
    api: "https://api-stg.locatedo.com",
    googleClientId: "238700922224-fiiu5qmdjv88rio10h1etfovg2i90ece.apps.googleusercontent.com",
  },
};
const appleServicesId = "com.locatedo.LocateDo.web";

const env = new URLSearchParams(location.search).get("env") === "stg" ? environments.stg : environments.prod;

let pending = null;

function show(id) {
  for (const el of document.querySelectorAll("[data-state]")) {
    el.hidden = el.id !== id;
  }
}

function randomNonce() {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}

async function sha256Hex(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, "0")).join("");
}

async function setUpApple() {
  const nonce = randomNonce();
  const button = document.getElementById("appleid-signin");
  if (matchMedia("(prefers-color-scheme: dark)").matches) {
    button.dataset.color = "white";
  }
  document.addEventListener("AppleIDSignInOnSuccess", (event) => {
    pending = {
      procedure: "DeleteAccountWithApple",
      body: { identityToken: event.detail.authorization.id_token, nonce },
    };
    show("confirm");
  });
  document.addEventListener("AppleIDSignInOnFailure", (event) => {
    if (event.detail && event.detail.error === "popup_closed_by_user") {
      return;
    }
    show("failed");
  });
  AppleID.auth.init({
    clientId: appleServicesId,
    redirectURI: location.origin + location.pathname,
    nonce: await sha256Hex(nonce),
    usePopup: true,
  });
}

function setUpGoogle() {
  const nonce = randomNonce();
  google.accounts.id.initialize({
    client_id: env.googleClientId,
    nonce,
    ux_mode: "popup",
    callback: (response) => {
      pending = {
        procedure: "DeleteAccountWithGoogle",
        body: { idToken: response.credential, nonce },
      };
      show("confirm");
    },
  });
  google.accounts.id.renderButton(document.getElementById("google-button"), {
    type: "standard",
    theme: "outline",
    size: "large",
    shape: "rectangular",
    width: 220,
    text: "signin_with",
    locale: document.documentElement.lang,
  });
}

async function deleteAccount() {
  if (!pending) {
    return;
  }
  show("deleting");
  try {
    const res = await fetch(`${env.api}/locatedo.account.v1.AccountService/${pending.procedure}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(pending.body),
    });
    pending = null;
    if (!res.ok) {
      show("failed");
      return;
    }
    const body = await res.json();
    show(body.deleted ? "deleted" : "not-found");
  } catch {
    pending = null;
    show("failed");
  }
}

setUpApple();
document.getElementById("delete-button").addEventListener("click", deleteAccount);
document.getElementById("cancel-button").addEventListener("click", () => {
  pending = null;
  show("sign-in");
});
for (const el of document.querySelectorAll("[data-retry]")) {
  el.addEventListener("click", () => show("sign-in"));
}

window.onGoogleLibraryLoad = setUpGoogle;
