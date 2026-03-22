# Visual Pass Notes
## Visual Design Spec: Analog Photo Journal

## 1\. Typography (OFL / Apache Licensed)

_Selected for high legibility in outdoor and low-light conditions._

| Font Name | Usage | Reason |
| --- | --- | --- |
| **JetBrains Mono** | Numerical Data (ISO, Shutter, f/) | Increased x-height makes small numbers readable in glare. |
| **Space Grotesk** | UI Headers / Labels | "Retro-future" 70s look; wide characters aid fast scanning. |
| **Share Tech Mono** | Widget / Top Plate Display | Mimics digital camera LCDs for a technical aesthetic. |

---

## 2\. Color Palettes (Hex Codes)

### A. High-Noon (Direct Sunlight)

_Goal: Maximum Luminance Contrast. Avoids pure white to prevent "bloom."_

*   **Background:** `#000000` (True Black - Best for OLED)
*   **Primary Text:** `#FFD700` (Safety Gold - Highest visibility)
*   **Secondary Data:** `#D1D1D1` (Light Gray)
*   **Active State:** `#00FF00` (Signal Green)

### B. Golden Hour (Evening / Indoor)

_Goal: Warm, nostalgic feel that reduces blue-light strain._

*   **Background:** `#121212` (Off-Black)
*   **Primary Text:** `#FFBF00` (Amber)
*   **Secondary Data:** `#A28089` (Dusty Mauve)
*   **Accent/Buttons:** `#F7882F` (Burnt Orange)

### C. Darkroom Safe (Night Vision)

_Goal: Preserve rhodopsin and prevent paper fogging._

*   **Background:** `#000000` (True Black)
*   **Primary Text:** `#FF0000` (Pure Red - 650nm+ wavelength)
*   **Secondary Data:** `#800000` (Deep Maroon)
*   **Interactive:** `#FF4500` (Orange-Red - Use sparingly)

---

## 3\. Licensing Compliance Links

_Use these to generate your "About/Legal" screen._

*   **Google Fonts (OFL):** [fonts.google.com](https://fonts.google.com) - All fonts here are free for commercial/app use.
*   **License Generator:** [tldrlegal.com](https://tldrlegal.com) - Good for summarizing the SIL OFL or Apache 2.0 terms.
*   **OSI Approved Licenses:** [opensource.org/licenses](https://opensource.org/licenses)

---

## 1\. Ergonomic Mapping (The "Thumb Zone")

*   **Active UI Area:** All primary interactive controls (sliders, log button) are restricted to the **bottom 60%** of the screen for one-handed "eye-to-viewfinder" operation.
*   **Delta Paradigm:** Successive logs inherit the previous frame's settings.
*   **The "Two-Speed" Interface:**
    *   **Widget/Haptics:** For "Camera-Up" moments (Aperture/Shutter adjustments).
    *   **Full Screen:** For "Camera-Down" moments (Lens swaps, Filter changes).

## 2\. Haptic Feedback & Detents

*   **The "Mechanical" Click:** Double-pulse haptic for a "closed" or "slower" setting change; long single pulse for "opening" or "faster."
*   **Concurrency Guard:** Haptic triggers are decoupled from the DAO/Database transaction layer to ensure zero input lag.
*   **Directional Toggle:** Provide a setting to flip "Increase/Decrease" directions to match specific vintage systems (e.g., Nikon-style vs. Leica-style clockwise rotation).

## 3\. Sunlight-Ready Color Palettes

| Mode | Background | Primary Text | Contrast Use |
| --- | --- | --- | --- |
| **High-Noon** | `#000000` | `#FFD700` (Gold) | Maximum visibility for Fresno sun glare. |
| **Golden Hour** | `#121212` | `#FFBF00` (Amber) | Reduced blue-light for street photography. |
| **Darkroom Safe** | `#000000` | `#FF0000` (Red) | Preserves night vision; wont fog film/paper. |

## 4\. Typography & Licensing (SIL OFL 1.1)

_All fonts listed are Free/Open Source for bundling in apps._

*   **JetBrains Mono:** Primary Numerical Data (High x-height for readability).
*   **Space Grotesk:** Headers and Labels (Retro-technical aesthetic).
*   **Share Tech Mono:** Widget Display (Mimics digital camera LCDs).

---

## 5\. Credits & Legal Boilerplate (Draft)

_Drop this into your "About" or "Settings > Licenses" screen._

**App Attribution:** "Designed for the analog workflow by \[Your Name/Handle\]. This app is provided 'as-is' without warranty. Please support the development by leaving a tip if this tool helps your process."

**Font Credits:**

*   **JetBrains Mono:** Copyright © 2020, JetBrains s.r.o. Licensed under SIL Open Font License 1.1.
*   **Space Grotesk:** Copyright © 2020, Florian Karsten. Licensed under SIL Open Font License 1.1.
*   **Share Tech Mono:** Copyright © 2012, Carrois Corporate Design. Licensed under SIL Open Font License 1.1.

_Note: Bundling these fonts requires including the full OFL 1.1 license text in your assets folder (usually_ `_assets/licenses/OFL.txt_`_)._

---

Yeah the second option is cleaner. Instead of:

```
Film expiry date
[Optional  >]
```

Just:

```
[Set expiry date (optional)  >]
```

One row, self-explanatory, recovers the vertical space the label was taking. Consistent with how other optional fields are handled — the row itself communicates its purpose and optionality.