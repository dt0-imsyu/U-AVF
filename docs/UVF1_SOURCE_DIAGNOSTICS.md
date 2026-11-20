# Bounded UVF1 source diagnostics

Version1/header40/AVC codec1 remain unchanged. Kind6 is optional UTF-8 source
metadata, not an AVC access unit, frame, presentation or EOS. Header length is
1..1024; generation is the existing connection generation; sequence/PTS/reserved
are zero. Source width/height are1..4096 and describe source inventory only;
metadata does not authorize a differently sized video frame. Kind2 still uses
the existing strict expected dimensions, sequence order and4MiB AU bound.
Kinds3/4/5 retain EOS/handshake/fixed-window semantics.

Windows relay rewrites kind6 generation only, not PTS; it does not increment
frame count or last video sequence. Android consumes kind6 before MediaCodec,
does not change frame/order counters, and logs at most1MiB metadata per stream.
Encoder inventory cache is bounded32KiB before stdout is ready; chunks1024B.
No second socket, decoder, shell socket, arbitrary guest command or file reader.
Linux senders that emit only existing packet kinds remain unchanged.

Actual native sender1371B chunk test plus following video frame ordering PASS.
Actual Java validator18 positive/negative tests PASS. Runtime tested in isolated
Windows APK E4D2656D...C4ED5EB8, not yet a production Linux APK release.
Metrics arrive live in encoded-display-report.txt on Android; NTFS durability
is no longer required to observe DXGI HRESULTs/effective encoder parameters.
