# Update AI Loading Animation and Ensure Height

The user wants the "分析中..." (Analyzing...) animation on the home screen to be replaced with the three-dot "Thinking" animation used when recording an expense. Additionally, this 4-line space must be maintained even during this loading phase.

## Proposed Changes

### UI Changes

#### [HomeScreen.kt](file:///Users/xxxxholic/StudioProjects/nozokima/app/src/main/java/com/example/nozokima/ui/screens/HomeScreen.kt)

- In `AiAnalysisSection`, replace the `statusLabel` text ("分析中...") with the `ThinkingAnimation()` component.
- Ensure the `Box` or `Column` containing the status/text has a minimum height corresponding to 4 lines (approx. 72dp) during the loading/generating phase.

## Verification Plan

### Manual Verification
- Deploy the app and trigger AI generation on the home screen.
- Verify that the "Thinking" animation (three dots) is displayed instead of "分析中...".
- Confirm that the AI box maintains its 4-line height during the animation and transition to the actual response.
