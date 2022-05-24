# Arduino Program

- Implement network request timeouts in `handleCommand()` (command.h) so the engineers can tune how long network operations can stall each loop
- Add a timestamp to data being sent to the app rather than generating the timestamp in the app to avoid mislabeling the data when networking latency is high
- Make `enum ServerStatus` (command.h) more robust -- different error values, etc.

# Android App

- Implement graphing for non-temperature sensors
- Clean up the appearance of graphs (axis labels, off-the-screen drawing, etc.)
- Test the app on non-XXHDPI devices and touch up the UI
- Handle error responses from the server
- Handle socket errors, such as attempting to connect to a non-existent server
- Other quality-of-life and polishing changes. Some ideas:
    - Allow more app UI configuration
    - Show a spinning icon while the app is connecting to the Arduino or sending a user-issued command (at the moment, this is limited to only dispensing water)
    - Show a trend line and/or extrapolation in the graphs based on received data

# Others

- Port the Android app to iOS
- Generate documentation for the project with [Sphinx](https://www.sphinx-doc.org)
- Test how the project behaves with poor network connections (dropped packets, high latency, etc.)
- Double-check to see if the licensing-related copyright should be done under WSU or all the contributors. If changed, the `LICENSE` file and `copyright` string in the app's `strings.xml` should be changed.
