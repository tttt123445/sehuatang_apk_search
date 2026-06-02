# Android Crawler Spec Delta

## ADDED Requirements

### Requirement: XTunnel readiness-gated proxy use

When the user enables embedded XTunnel, the app SHALL only route crawl, check, and image requests through the local proxy after native status reports ready and the local proxy port is reachable.

#### Scenario: Proxy is not ready yet

- GIVEN embedded XTunnel is enabled
- AND native readiness or local port health has not passed
- WHEN the app performs a network request
- THEN the request SHALL use system VPN/direct networking instead of an unavailable local proxy
- AND the app SHALL show a visible fallback status message once per failure window.

#### Scenario: Startup is triggered multiple times

- GIVEN embedded XTunnel startup is already in progress
- WHEN the user taps startup-related actions again
- THEN the app SHALL reuse the existing startup task
- AND SHALL avoid creating duplicate native startup attempts.

### Requirement: Long operations expose busy state

Crawl, check, and manual XTunnel startup SHALL show progress status and disable duplicate long-running actions until the active operation completes.

#### Scenario: Crawl is running

- GIVEN crawl is in progress
- WHEN the user views the action buttons
- THEN crawl, check, and manual XTunnel startup controls SHALL be disabled
- AND copy/share controls SHALL remain disabled only if no selected magnets are exportable.

### Requirement: Results expose counts separately from network status

The app SHALL keep network status and result statistics separate so the user can see both the current network mode and the current crawl/export counts.

#### Scenario: Crawl completes

- GIVEN crawl completes with found items
- WHEN results render
- THEN the result summary SHALL include item count, total magnet count, image-bearing item count, and exportable selected magnet count.

### Requirement: Image preview supports recovery and navigation

When a result item has multiple images, the preview dialog SHALL support previous/next navigation, retry after failure, and opening the original thread.

#### Scenario: Preview image fails

- GIVEN image preview loading fails
- WHEN the dialog displays failure state
- THEN the dialog SHALL show a retry control
- AND retry SHALL re-run the same image request.

#### Scenario: Item has multiple images

- GIVEN a result item has more than one image
- WHEN the preview dialog is open
- THEN the dialog SHALL display position text
- AND SHALL enable previous/next actions only when an adjacent image exists.

### Requirement: Image cache writes avoid partial target files

Image cache writes SHALL write to a temporary file first and expose the target cache file only after the write has completed.

#### Scenario: Image stream is interrupted

- GIVEN an image download stream fails before completion
- WHEN cache write exits
- THEN the target cache file SHALL not be replaced by a partial stream.

#### Scenario: Cache promotion fails

- GIVEN an existing target cache file is present
- AND a completed temporary file cannot be promoted
- WHEN cache replacement exits
- THEN the previous target cache file SHALL be restored when possible.
