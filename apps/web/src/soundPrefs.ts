const KEY = 'math-classroom-boot-chime-muted'

/** Default unmuted (false). Persist mute preference in localStorage. */
export function loadBootChimeMuted(): boolean {
  try {
    return localStorage.getItem(KEY) === '1'
  } catch {
    return false
  }
}

export function saveBootChimeMuted(muted: boolean): void {
  try {
    localStorage.setItem(KEY, muted ? '1' : '0')
  } catch {
    /* ignore quota / private mode */
  }
}

/** Preferred path; WelcomeSplash falls back to /sounds/ if needed. */
export const BOOT_CHIME_SRC = '/sfx/boot-chime.wav'
export const BOOT_CHIME_FALLBACK = '/sounds/boot-chime.wav'
