# Capstone Project: Flowerpot App

## Overview

This project's goal is to create an Internet-of-Things (IoT) flowerpot that can be remotely controlled with an app. The intended use is primarily for education. There are three main components:

1. The flowerpot hardware and source code maintained by a team of mechanical engineers. This consists of a flowerpot, an Arduino microcontroller, and sensors/actuators attached to the Arduino.
2. Header files managing the communication over the network on the Arduino, maintained by the CS team.
3. An app that communicates with the Arduino to allow users to control various aspects of the flowerpot system.

## Flowerpot System

The system maintained by the CS team is small and can likely be managed by one or two people. The `FlowerpotArduino` directory contains the source code for this, with `FlowerpotArduino.ino` being the main file. The source code is well-documented with information about its dependencies, configuration options, and the structure of the networking protocol used to communicate with the app.

## User-Facing App

The app, found in the `FP_Test` directory, targets Android 26 (Oreo). It depends on the [GraphView](https://github.com/jjoe64/GraphView) library. This app was developed in Android Studio with a focus on XXHDPI devices such as the Pixel 4; the interface may need updating on other devices.

### Pages

Currently, the app has six main pages:

1. The main page shown on startup, which serves as a hub for accessing the other pages.
2. The *Connect* page, which allows the user to enter an IPv4 address and port number to connect to the Arduino.
3. The *Manage* page, which allows the user to manage different aspects of the flowerpot (currently for dispensing water).
4. The *Data* page, which shows the different sensors attached to the Arduino and lets the user view a real-time graph of each sensor's data.
5. The *Credits* page, containing all of the contributors' names.
6. The *License* page, which contains a copy of the MIT License that this project is licensed under.

Although the *Data* page's temperature graphing is functional, graphing for the rest of the sensors is currently unimplemented.

## Hints for Future Capstone Teams

- You probably got this project in a ZIP file from Hakan Gurocak -- make a new Git repo on GitHub, GitLab, or some other repo-hosting site to make life easier when working on the project with multiple people.
- The first files to read through, other than this one, are `FlowerpotArduino/FlowerpotArduino.ino` and (deep breath) `FP_Test/app/src/main/java/com/example/fp_test/MainActivity.kt`.
- For ideas on where to start working, see `TODO.md`.
- Be careful when adjusting the Android app's networking code. Java and Kotlin both have a lot of footguns when it comes to low-level bit operations, although Kotlin is more bearable.

## Contributors

- Glenn Beckers III
- Jared Kangas
- Henry Unruh
- Rawad Bader
- Yuhao Liu
