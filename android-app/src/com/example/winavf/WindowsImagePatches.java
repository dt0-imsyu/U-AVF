// Mixed provenance: inherited material retains Apache-2.0.
// Original new U-AVF contributions: see LICENSE_SCOPE.md and LICENSE-UAVF.txt.
// No third-party or previously granted rights are withdrawn.
package com.example.winavf;

import static com.example.winavf.IoUtil.rootMessage;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.function.Consumer;

/** Applies and rolls back small patches to the legacy Windows diagnostic image. */
final class WindowsImagePatches {
   private static final String ACTIVE_PATCH = "active-image-patch.bin";
   private final Context context;
   private final Consumer<String> notice;

   WindowsImagePatches(Context context, Consumer<String> notice) {
      this.context = context;
      this.notice = notice;
   }

   void applyStaged(File payload, File baseline) throws Exception {
      File stagedPatch = new File(context.getExternalFilesDir(null), "winavf-image-patch.bin");
      if (!stagedPatch.isFile()) return;

      File activePatch = new File(payload, ACTIVE_PATCH);
      if (activePatch.exists()) {
         if (!ImagePatch.targetsBaseline(activePatch, baseline)) {
            throw new IllegalStateException("An earlier image patch is still active; roll it back before applying another patch");
         }
         if (!activePatch.delete()) throw new IOException("Could not discard the unapplied image patch record");
         notice.accept("Discarded a patch record that was never applied to the baseline image.");
      }

      GuestMediaFiles.copy(stagedPatch, activePatch, -1L, true,
            () -> notice.accept("Copying Windows setup medium into app-private storage…"));
      try {
         ImagePatch.apply(activePatch, baseline, false);
      } catch (Exception error) {
         notice.accept("Patch was not accepted; active bundle is retained for inspection/rollback.");
         throw error;
      }
      if (!stagedPatch.delete()) notice.accept("Patch applied; external staging copy could not be deleted.");
      notice.accept("Applied verified small image patch: " + activePatch.length() + " bytes");
   }

   void rollback() {
      File report = new File(context.getExternalFilesDir(null), "rollback-report.txt");
      File payload = new File(context.getFilesDir(), "payload");
      File baseline = new File(payload, "win11-gop-ebs-r1.img");
      File activePatch = new File(payload, ACTIVE_PATCH);
      try {
         if (!baseline.isFile()) throw new IOException("No app-private runtime image exists yet");
         if (!activePatch.isFile()) throw new IOException("No active image patch exists to roll back");

         ImagePatch.apply(activePatch, baseline, true);
         if (!activePatch.delete()) throw new IOException("Rollback passed but the active patch record could not be removed");
         writeReport(report, "PASS", "BASELINE_SHA256=" + Digest.hex(Digest.sha256File(baseline)));
         notice.accept("Small image patch rolled back and the runtime image was verified.");
      } catch (Exception error) {
         try {
            writeReport(report, "FAIL", "ERROR=" + rootMessage(error));
         } catch (IOException reportError) {
            android.util.Log.w("U-AVF-image", "Could not write the rollback report", reportError);
         }
         notice.accept("ROLLBACK FAILED: " + rootMessage(error));
      }
   }

   private static void writeReport(File report, String result, String detail) throws IOException {
      try (PrintWriter writer = new PrintWriter(new FileOutputStream(report, false))) {
         writer.println("RESULT=" + result);
         writer.println(detail);
         if (writer.checkError()) throw new IOException("Could not write the Windows image rollback report");
      }
   }
}
