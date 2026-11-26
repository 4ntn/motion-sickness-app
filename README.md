# Motion cues

Motion cues is an Android app that shows an overlay while you use your phone in a car, bus, or train.

You need to activate the overlay yourself, it is not automatic.

It works similarly to vehicle motion cues on iPhone and aims to reduce motion sickness by drawing dots that drift with the vehicle's motion.

## Features

- **Motion-driven overlay**: dots are drawn over other apps and move with the vehicle's acceleration and turns. Works in portrait and landscape, including with rotation lock on. Touches pass through to the app underneath.
- **Quick start and stop**: toggle the cues from the app, from the Quick Settings tile, or with the Stop button in the ongoing notification.
- **Layouts**: choose where the dots appear, with a live preview of each option:
  - **Columns**: bands along the left and right edges.
  - **Edges**: bands along all four edges.
  - **Focus**: dots fade out towards the center, keeping the middle of the screen clear.
- **Dot styles**: pick a style that stays visible over your content:
  - White
  - Black
  - Half white, half black
  - White with a black outline
  - Black with a white outline
  - Color, with a hue picker to choose any color
- **Size and opacity**: sliders to adjust how large and how visible the dots are.
- **Live updates**: changes to layout, style, color, size, and opacity apply to the overlay immediately and are remembered between sessions.

## Permissions

- **Display over other apps**: required to draw the overlay. The app asks you to grant it the first time.
- **Notifications** (Android 13+): used for the ongoing notification while the cues are running.

## License

This project is licensed under `GPL-3.0-only`.

See [LICENSE](LICENSE) for the full text.
