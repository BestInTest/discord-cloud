# Discord Cloud

Store and share files using Discord.

## How to use

1. Install Java 17 or newer.
2. Download the latest release from the [releases page](https://github.com/BestInTest/discord-cloud/releases).
3. Extract the downloaded archive.
4. Run `discord-cloud-gui-2.X.jar` (`discord-cloud.exe` on Windows).
5. Configure webhook link or bot token and channel ID in the settings.

### Which archive should I download?

- **Windows / Linux archives** include FFmpeg binaries.
  - Choose one of these if you do not have FFmpeg installed.
- **Slim archive** does not include FFmpeg binaries.
  - Choose this version if FFmpeg is already installed on your system.
- **Windows archive** also includes an `.exe` file, so you can run the application without opening a terminal.

## CLI modules

You can also use the modules from the command line.

```bash
java -jar uploader-2.0.jar
```

In general, modules can be started with:

```bash
java -jar <file>
```

## Compiling

Run the following command in the main project directory:

```bash
mvn clean package
```

Compiled files will be available in the `build` directory.

## Special thanks

- Snajperekk — testing, feedback, and icon for the GUI
- Friends — testing
