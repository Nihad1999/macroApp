package com.nihadabdulla.imageformatconverter;

import android.app.Activity;

import android.content.Context;

import android.graphics.Bitmap;

import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;


import android.os.Handler;
import android.os.Looper;

import android.provider.MediaStore;
import android.util.Log;
import android.util.Pair;

import android.widget.Toast;


import org.apache.commons.io.IOUtils;


import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.ExecuteCallback;
import com.arthenica.mobileffmpeg.FFmpeg;
import com.arthenica.mobileffmpeg.Statistics;
import com.arthenica.mobileffmpeg.StatisticsCallback;
import com.facebook.animated.webp.WebPImage;
import com.facebook.imagepipeline.common.ImageDecodeOptions;
import com.waynejo.androidndkgif.GifEncoder;

import android.media.MediaMetadataRetriever;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Arrays;

import pl.droidsonroids.gif.GifDrawable;


public class ImageConverter {
    public static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static SelectedImagesAdapter adapter;

    public static void setAdapter(SelectedImagesAdapter selectedImagesAdapter) {
        adapter = selectedImagesAdapter;
    }
    public static int TotalLoopCounter = 0;


    public static Uri convert(Context context, Uri sourceUri, String sourceFormat, String destinationFormat, int position, int TotalConversion, MainActivity.ConversionSettings settings, int frameNumberConversion) {


        try {
            // Handle animated formats
            if (isAnimatedFormat(sourceFormat) && isAnimatedFormat(destinationFormat)) {
                return handleAnimatedConversion(context, sourceUri, sourceFormat, destinationFormat, position, TotalConversion, settings);
            }


            // Handle static image formats
            Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), sourceUri);
            File cacheDir = context.getCacheDir();
            File outputFile = new File(cacheDir, "converted_" + System.currentTimeMillis() + "." + destinationFormat);
            FileOutputStream fos = new FileOutputStream(outputFile);

            ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(), sourceUri);
            Drawable drawable = ImageDecoder.decodeDrawable(source);
            //AnimatedImageDrawable drawable = (AnimatedImageDrawable) ImageDecoder.decodeDrawable(source);
            int durationMs;
            int frameCount;
            int quality=settings.getQuality();;
            if(sourceFormat.equals("webp") ){
                Pair<Integer, Integer> info = ImageConverter.getWebPInfo(context, sourceUri);
                durationMs = info.first;
                frameCount = info.second;

            } else {

                durationMs = 0;
                frameCount = 0;
            }

            switch (destinationFormat.toLowerCase()) {
                case "jpg":
                case "jpeg":
                    Log.d("Check", "I am in case JPEG ");

                    if (drawable instanceof AnimatedImageDrawable && sourceFormat.equals("webp")) {

                        new Thread(() -> {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    MainActivity.textView.setText("Starting convert to JPEG");
                                    adapter.updateProgress(position, 0);
                                }
                            });

                            AnimatedImageDrawable animatedWebP = (AnimatedImageDrawable) drawable;
                            Log.d("ConversionDebug", "Starting frame extraction. Frame count: " + frameCount);
                            animatedWebP.start();

                            for (int i = 0; i < frameNumberConversion; i++) {
                                try {
                                    Thread.sleep(durationMs / frameCount);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                                int remainingTime = frameNumberConversion - i - 1;
                                final float progress = (i * 100f) / frameNumberConversion;
                                if (i % 5 == 0) {
                                    Log.d("Check", "I am in if check");
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            MainActivity.textView.setText("Remeining Second: " + remainingTime );
                                            adapter.updateProgress(position, (int) progress);
                                        }
                                    });
                                }
                                Bitmap frame = drawableToBitmap(drawable);

                                if (i == frameNumberConversion - 1) {
                                    TotalLoopCounter = TotalLoopCounter +1;
                                    try {
                                        frame.compress(Bitmap.CompressFormat.JPEG, quality, fos);
                                        fos.close();
                                        fos.flush();
                                        if(TotalLoopCounter < TotalConversion){
                                            mainHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    // Update current image's progress and completion status
                                                    adapter.updateProgress(position, 100);
                                                    adapter.setConversionComplete(position);
                                                }
                                            });
                                        }
                                        else {
                                            mainHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Log.d("Position complete check", String.valueOf(position));
                                                    // Update all images' progress and completion status

                                                    adapter.updateProgress(position, 100);
                                                    adapter.setConversionComplete(position);

                                                    MainActivity.textView.setText("Conversion completed!");
                                                    Toast.makeText(context, "Conversion completed!", Toast.LENGTH_SHORT).show();
                                                    ((Activity) context).findViewById(R.id.downloadButton).setEnabled(true);
                                                    ((Activity) context).findViewById(R.id.downloadButton).setAlpha(1.0f);
                                                    TotalLoopCounter = 0;


                                                }
                                            });
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }).start();
                    }
                    else {

                        new Thread(() -> {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    MainActivity.textView.setText("Starting converting to JPEG");
                                    adapter.updateProgress(position, 0);
                                }
                            });
                            try {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos);
                                fos.close();

                                TotalLoopCounter = TotalLoopCounter + 1;
                                if (TotalLoopCounter < TotalConversion) {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            // Update current image's progress and completion status
                                            adapter.updateProgress(position, 100);
                                            adapter.setConversionComplete(position);
                                        }
                                    });
                                } else {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Log.d("Position complete check", String.valueOf(position));
                                            // Update all images' progress and completion status

                                            adapter.updateProgress(position, 100);
                                            adapter.setConversionComplete(position);

                                            MainActivity.textView.setText("Conversion completed!");
                                            Toast.makeText(context, "Conversion completed!", Toast.LENGTH_SHORT).show();
                                            ((Activity) context).findViewById(R.id.downloadButton).setEnabled(true);
                                            ((Activity) context).findViewById(R.id.downloadButton).setAlpha(1.0f);
                                            TotalLoopCounter = 0;


                                        }
                                    });
                                }
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            }
                        }).start();

                    }
                    break;

                case "png":
                    Log.d("Check", "I am in case png ");
                    if (drawable instanceof AnimatedImageDrawable && sourceFormat.equals("webp")) {

                        new Thread(() -> {
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    MainActivity.textView.setText("Starting convert to PNG");
                                    adapter.updateProgress(position, 0);
                                }
                            });

                            AnimatedImageDrawable animatedWebP = (AnimatedImageDrawable) drawable;
                            Log.d("ConversionDebug", "Starting frame extraction. Frame count: " + frameCount);
                            animatedWebP.start();

                            for (int i = 0; i < frameNumberConversion; i++) {
                                try {
                                    Thread.sleep(durationMs / frameCount);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }

                                int remainingTime = frameNumberConversion - i - 1;
                                final float progress = (i * 100f) / frameNumberConversion;
                                if (i % 5 == 0) {
                                    Log.d("Check", "I am in if check");
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            MainActivity.textView.setText("Remeining Second: " + remainingTime );
                                            adapter.updateProgress(position, (int) progress);
                                        }
                                    });
                                }
                                Bitmap frame = drawableToBitmap(drawable);

                                if (i == frameNumberConversion - 1) {
                                    TotalLoopCounter = TotalLoopCounter +1;
                                    try {
                                        frame.compress(Bitmap.CompressFormat.PNG, quality, fos);
                                        fos.close();
                                        fos.flush();
                                        if(TotalLoopCounter < TotalConversion){
                                            mainHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    // Update current image's progress and completion status
                                                    adapter.updateProgress(position, 100);
                                                    adapter.setConversionComplete(position);
                                                }
                                            });
                                        }
                                        else {
                                            mainHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Log.d("Position complete check", String.valueOf(position));
                                                    // Update all images' progress and completion status

                                                    adapter.updateProgress(position, 100);
                                                    adapter.setConversionComplete(position);

                                                    MainActivity.textView.setText("Conversion completed!");
                                                    Toast.makeText(context, "Conversion completed!", Toast.LENGTH_SHORT).show();
                                                    ((Activity) context).findViewById(R.id.downloadButton).setEnabled(true);
                                                    ((Activity) context).findViewById(R.id.downloadButton).setAlpha(1.0f);
                                                    TotalLoopCounter = 0;


                                                }
                                            });
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }).start();
                    }
                    else{
                        new Thread(() -> {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                MainActivity.textView.setText("Starting convert to png");
                                adapter.updateProgress(position, 0);
                            }
                        });
                        try {
                            bitmap.compress(Bitmap.CompressFormat.PNG, quality, fos);
                            fos.close();
                            fos.flush();
                            TotalLoopCounter = TotalLoopCounter + 1;
                            if (TotalLoopCounter < TotalConversion) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Update current image's progress and completion status
                                        adapter.updateProgress(position, 100);
                                        adapter.setConversionComplete(position);
                                    }
                                });
                            } else {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.d("Position complete check", String.valueOf(position));
                                        // Update all images' progress and completion status

                                        adapter.updateProgress(position, 100);
                                        adapter.setConversionComplete(position);

                                        MainActivity.textView.setText("Conversion completed!");
                                        Toast.makeText(context, "Conversion completed!", Toast.LENGTH_SHORT).show();
                                        ((Activity) context).findViewById(R.id.downloadButton).setEnabled(true);
                                        ((Activity) context).findViewById(R.id.downloadButton).setAlpha(1.0f);
                                        TotalLoopCounter = 0;


                                    }
                                });
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                    }

                    break;

                case "webp":
                    Log.d("Check", "I am in case webp ");
                    new Thread(() -> {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                MainActivity.textView.setText("Starting convert to webp");
                                adapter.updateProgress(position, 0);
                            }
                        });
                        try {
                            bitmap.compress(Bitmap.CompressFormat.WEBP, quality, fos);
                            fos.close();
                            fos.flush();
                            TotalLoopCounter = TotalLoopCounter + 1;
                            if (TotalLoopCounter < TotalConversion) {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // Update current image's progress and completion status
                                        adapter.updateProgress(position, 100);
                                        adapter.setConversionComplete(position);
                                    }
                                });
                            } else {
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Log.d("Position complete check", String.valueOf(position));
                                        // Update all images' progress and completion status

                                        adapter.updateProgress(position, 100);
                                        adapter.setConversionComplete(position);

                                        MainActivity.textView.setText("Conversion completed!");
                                        Toast.makeText(context, "Conversion completed!", Toast.LENGTH_SHORT).show();
                                        ((Activity) context).findViewById(R.id.downloadButton).setEnabled(true);
                                        ((Activity) context).findViewById(R.id.downloadButton).setAlpha(1.0f);
                                        TotalLoopCounter = 0;


                                    }
                                });
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();

                    break;
                case "mp4":
                    new Thread(() -> {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                MainActivity.textView.setText("Starting convert to mp4");
                                adapter.updateProgress(position, 0);
                            }
                        });

                        try{
                            File tempInputFile = new File(context.getCacheDir(), "temp_input_" + System.currentTimeMillis() + ".jpg");

                            // Copy from URI to temp file
                            InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
                            FileOutputStream outputStream = new FileOutputStream(tempInputFile);

                            byte[] buffer = new byte[8192];
                            int length;
                            while ((length = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);
                            }

                            outputStream.flush();
                            outputStream.close();
                            inputStream.close();

                            // Debug
                            Log.d("FileDebug", "Temp file created: " + tempInputFile.getAbsolutePath());
                            Log.d("FileDebug", "File size: " + tempInputFile.length());



                            String[] cmd = {
                                    "-framerate", "1",
                                    "-i", tempInputFile.getAbsolutePath(),
                                    "-vf", "scale=1920:1080",  // Force to 1080p with square pixels
                                    "-pix_fmt", "yuv420p",
                                    "-preset", "medium",
                                    "-crf", String.valueOf(quality),
                                    "-t", "1",
                                    "-loop", "1",
                                    "-y",
                                    outputFile.getAbsolutePath()
                            };







                            final int TOTAL_FRAMES = 25;

                            Config.enableStatisticsCallback(statistics -> {
                                int currentFrame = statistics.getVideoFrameNumber();
                                int progress = (currentFrame * 100) / TOTAL_FRAMES;

                                mainHandler.post(() -> {
                                    adapter.updateProgress(position, progress);
                                    Log.d("Progress", "Current progress: " + progress + "%");
                                });
                            });

                            FFmpeg.executeAsync(cmd, (executionId, returnCode) -> {
                                Config.enableStatisticsCallback(null);
                                tempInputFile.delete();
                                if (returnCode == 0) {
                                    TotalLoopCounter = TotalLoopCounter + 1;

                                    if (TotalLoopCounter < TotalConversion) {
                                        Log.d("Position else if check", String.valueOf(position));
                                        adapter.updateProgress(position, 100);
                                        adapter.setConversionComplete(position);
                                    } else if (TotalLoopCounter == TotalConversion) {
                                        mainHandler.post(() -> {
                                            adapter.updateProgress(position, 100);
                                            adapter.setConversionComplete(position);
                                            MainActivity.textView.setText("Conversion completed successfully!");
                                            Toast.makeText(context, "Conversion completed!", Toast.LENGTH_SHORT).show();
                                            ((Activity) context).findViewById(R.id.downloadButton).setEnabled(true);
                                            ((Activity) context).findViewById(R.id.downloadButton).setAlpha(1.0f);
                                            TotalLoopCounter = 0;
                                        });
                                    }
                                }


                                Log.d("Cleanup", "Temp file deleted");
                            });


                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();
                    break;
            }



            return Uri.fromFile(outputFile);

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Uri handleAnimatedConversion(Context context, Uri sourceUri,
                                               String sourceFormat, String destinationFormat, int position, int TotalConversion, MainActivity.ConversionSettings settings) throws IOException {





        File cacheDir = context.getCacheDir();
        File outputFile = new File(cacheDir, "converted_" + System.currentTimeMillis() + "." + destinationFormat);
        InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);

        if (sourceFormat.equals("webp") && destinationFormat.equals("gif")) {
            try {
                Log.d("ConversionDebug", "Starting animated WebP to GIF conversion");
                Log.d("Position enter check", String.valueOf(position));

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                MainActivity.textView.setText("Starting animated WebP to GIF conversion");
                                adapter.updateProgress(position, 0);
                            }
                        });

                        try {
                            ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(), sourceUri);
                            Drawable drawable = ImageDecoder.decodeDrawable(source);
                            //AnimatedImageDrawable drawable = (AnimatedImageDrawable) ImageDecoder.decodeDrawable(source);
                            // Correct method names
                            Pair<Integer, Integer> info = ImageConverter.getWebPInfo(context, sourceUri);

                            int durationgetWebp = info.first;
                            int frameCountgetWebp = info.second;
                            // Calculate FPS
                            float fps = (float) (frameCountgetWebp * 1000) / durationgetWebp;
                            float frameDelayMs = (float) durationgetWebp / frameCountgetWebp;

                            Log.d("WebP", "Duration: " + durationgetWebp + "ms, Frames: " + frameCountgetWebp + "FPS--" + fps + "Frame Delays--" + frameDelayMs);

                            if (drawable instanceof AnimatedImageDrawable) {
                                AnimatedImageDrawable animatedWebP = (AnimatedImageDrawable) drawable;


                                Log.d("ConversionDebug", "Animated WebP detected");
                                Log.d("ConversionDebug", "Image dimensions: " + drawable.getIntrinsicWidth() + "x" + drawable.getIntrinsicHeight());

                                int quality = settings.getQuality();

                                GifEncoder encoder = new GifEncoder();
                                GifEncoder.EncodingType encodingType;

                                // Change quality thresholds

                                if (quality >= 70) {
                                    encodingType = GifEncoder.EncodingType.ENCODING_TYPE_NORMAL_LOW_MEMORY;
                                    // Adjust color depth for higher quality
                                    encoder.setDither(true);  // Enable dithering for better color
                                    Log.d("QualityDebug", "High quality: Dithering enabled");
                                } else if (quality >= 30) {
                                    encodingType = GifEncoder.EncodingType.ENCODING_TYPE_NORMAL_LOW_MEMORY;
                                    encoder.setDither(false);
                                    Log.d("QualityDebug", "Medium quality: Standard encoding");
                                } else {
                                    encodingType = GifEncoder.EncodingType.ENCODING_TYPE_FAST;
                                    Log.d("QualityDebug", "Low quality: Fast encoding");
                                }



                                encoder.init(drawable.getIntrinsicWidth(),
                                        drawable.getIntrinsicHeight(),
                                        outputFile.getPath(),
                                        encodingType);

                                animatedWebP.start();
                                int totalFramesProcessed = 0;

                                for (int i = 0; i < frameCountgetWebp; i++) {

                                    int remainingTime = frameCountgetWebp - i - 1;
                                    final float progress = (i * 100f) / frameCountgetWebp;
                                    if (i % 5 == 0) {
                                        Log.d("Check", "I am in if check");
                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                MainActivity.textView.setText("Remeining Second: " + remainingTime );
                                                adapter.updateProgress(position, (int) progress);
                                            }
                                        });
                                    }

                                    Bitmap frame = drawableToBitmap(drawable);
                                    encoder.encodeFrame(frame, (int) frameDelayMs);
                                    Log.d("ConversionDebug", "Frame " + (i + 1) + " of " + frameCountgetWebp + " captured");
                                    frame.recycle();


                                }
                                encoder.close();

                                TotalLoopCounter = TotalLoopCounter +1;
                                if(TotalLoopCounter < TotalConversion){
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            // Update current image's progress and completion status
                                            adapter.updateProgress(position, 100);
                                            adapter.setConversionComplete(position);
                                        }
                                    });
                                }
                                else {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Log.d("Position complete check", String.valueOf(position));
                                            // Update all images' progress and completion status

                                            adapter.updateProgress(position, 100);
                                            adapter.setConversionComplete(position);

                                            MainActivity.textView.setText("Conversion completed!");
                                            Toast.makeText(context, "Conversion completed!", Toast.LENGTH_SHORT).show();
                                            ((Activity) context).findViewById(R.id.downloadButton).setEnabled(true);
                                            ((Activity) context).findViewById(R.id.downloadButton).setAlpha(1.0f);
                                            TotalLoopCounter = 0;



                                        }
                                    });
                                }


                                Log.d("ConversionDebug", "Conversion completed. Total frames processed: " + totalFramesProcessed);
                                Log.d("ConversionDebug", "Output file size: " + outputFile.length() + " bytes");
                            }
                            else{
                                Log.d("Webp type", "I am static webp )");
                                int quality = settings.getQuality();
                                File tempFile = new File(context.getCacheDir(), "temp_input.webp");

                                // Copy Uri content to temp file
                                InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
                                FileOutputStream fos = new FileOutputStream(tempFile);

                                byte[] buffer = new byte[1024];
                                int length;
                                while ((length = inputStream.read(buffer)) > 0) {
                                    fos.write(buffer, 0, length);
                                }

                                fos.close();
                                inputStream.close();


                                String inputPath = tempFile.getAbsolutePath();



                                String[] cmd = {
                                        "-i", inputPath,
                                        "-quality", String.valueOf(quality),
                                        // "-filter_complex", "scale=trunc(iw/2)*2:trunc(ih/2)*2",  // Ensure even dimensions
                                        outputFile.getAbsolutePath()
                                };
                                FFmpeg.execute(cmd);

                                // Debug logs
                                Log.d("ConvertDebug", "Input path: " + inputPath);
                                Log.d("ConvertDebug", "Output path: ");

                                TotalLoopCounter = TotalLoopCounter +1;
                                if(TotalLoopCounter < TotalConversion){
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            // Update current image's progress and completion status
                                            adapter.updateProgress(position, 100);
                                            adapter.setConversionComplete(position);
                                        }
                                    });
                                }
                                else {
                                    mainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            Log.d("Position complete check", String.valueOf(position));
                                            // Update all images' progress and completion status

                                            adapter.updateProgress(position, 100);
                                            adapter.setConversionComplete(position);

                                            MainActivity.textView.setText("Conversion completed!");
                                            Toast.makeText(context, "Conversion completed!", Toast.LENGTH_SHORT).show();
                                            ((Activity) context).findViewById(R.id.downloadButton).setEnabled(true);
                                            ((Activity) context).findViewById(R.id.downloadButton).setAlpha(1.0f);
                                            TotalLoopCounter = 0;


                                        }
                                    });
                                }


                                if (tempFile != null && tempFile.exists()) {
                                    boolean deleted = tempFile.delete();
                                    Log.d("FileDebug", "Temp file deleted: " + deleted);
                                }

                            }
                        } catch (Exception e) {
                            Log.e("ConversionDebug", "Error during conversion: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }).start();

                return Uri.fromFile(outputFile);
            } catch (Exception e) {
                Log.e("ConversionDebug", "Error during conversion: " + e.getMessage());
                e.printStackTrace();
            }
        }




        else if (sourceFormat.equals("gif") && destinationFormat.equals("webp")) {
            try {
                Log.d("ConversionDebug", "Starting animated GIF to WebP conversion");
                Log.d("Position enter check", String.valueOf(position));
                // Create unique file names for each conversion
                final File tempInputFile = new File(context.getCacheDir(), "temp_input_" + position + "_" + System.currentTimeMillis() + ".gif");
                //final File outputFile = new File(context.getCacheDir(), "converted_" + position + "_" + System.currentTimeMillis() + ".webp");
                final File uniqueOutputFile = new File(context.getCacheDir(), "converted_" + position + "_" + System.currentTimeMillis() + ".webp");

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                MainActivity.textView.setText("Starting GIF to WebP conversion");
                                adapter.updateProgress(position, 0);
                            }
                        });

                        try {
                            InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
                            FileOutputStream outputStream = new FileOutputStream(tempInputFile);

                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);

                            }

                            inputStream.close();
                            outputStream.close();

                            String inputPath = tempInputFile.getAbsolutePath();
                            String outputPath = outputFile.getAbsolutePath();

                            GifDrawable gifDrawable = new GifDrawable(inputPath);

                            // Get GIF information
                            int durationMs = gifDrawable.getDuration();
                            int totalFrames = gifDrawable.getNumberOfFrames();

                            // Calculate frame rate
                            float frameRate = (float) (totalFrames * 1000) / durationMs;

                            // Use these values for your conversion
                            Log.d("GIF", "Duration: " + durationMs + "ms, Frames: " + totalFrames + ", FPS: " + frameRate);

                            gifDrawable.recycle();






                            Log.d("FFmpegDebug", "Input Path: " + inputPath);
                            Log.d("FFmpegDebug", "Output Path: " + outputPath);

                            // Get quality and compression values from settings
                            int quality = settings.getQuality();         // 0-100
                            int compression = settings.getCompression(); // 0-6


                            String[] cmd = {
                                    "-i", inputPath,
                                    "-vcodec", "libwebp",
                                    "-quality", String.valueOf(quality),
                                    "-compression_level", String.valueOf(compression),
                                    "-loop", "0",
                                    outputPath
                            };



                            // Enable statistics callback
                            Config.enableStatisticsCallback(new StatisticsCallback() {
                                @Override
                                public void apply(Statistics statistics) {
                                    int currentFrame = statistics.getVideoFrameNumber();
                                    if (currentFrame > 0) {
                                        final float progress = (currentFrame * 100f) / totalFrames;
                                        mainHandler.post(() -> {
                                            adapter.updateProgress(position, (int) progress);
                                            MainActivity.textView.setText("Converting frame: " + currentFrame + "/" + totalFrames);
                                        });
                                    }
                                }
                            });

                            FFmpeg.executeAsync(cmd, new ExecuteCallback() {
                                @Override
                                public void apply(long executionId, int returnCode) {
                                    Log.d("FFmpegDebug", "Return code: " + returnCode);
                                    Log.d("FFmpegDebug", "Command: " + Arrays.toString(cmd));
                                    Log.d("FFmpegDebug", "Input path: " + inputPath);
                                    Log.d("FFmpegDebug", "Output path: " + outputPath);
                                    Config.enableStatisticsCallback(null);
                                    if (returnCode == 0) {
                                        TotalLoopCounter = TotalLoopCounter + 1;

                                        if (TotalLoopCounter < TotalConversion) {
                                            Log.d("Position else if check", String.valueOf(position));
                                            adapter.updateProgress(position, 100);
                                            adapter.setConversionComplete(position);
                                        }
                                        else if (TotalLoopCounter == TotalConversion) {
                                            mainHandler.post(() -> {
                                                adapter.updateProgress(position, 100);
                                                adapter.setConversionComplete(position);
                                                MainActivity.textView.setText("Conversion completed successfully!");
                                                Toast.makeText(context, "Conversion completed!", Toast.LENGTH_SHORT).show();
                                                ((Activity) context).findViewById(R.id.downloadButton).setEnabled(true);
                                                ((Activity) context).findViewById(R.id.downloadButton).setAlpha(1.0f);
                                                TotalLoopCounter = 0;
                                            });
                                        }
                                        if (tempInputFile != null && tempInputFile.exists()) {
                                            File[] files = tempInputFile.listFiles();
                                            if (files != null) {
                                                for (File file : files) {
                                                    boolean deleted = file.delete();
                                                    Log.d("FileDebug", "Deleted file: " + file.getName() + " - " + deleted);
                                                }
                                            }
                                            // Delete the temp directory itself
                                            boolean dirDeleted = tempInputFile.delete();
                                            Log.d("FileDebug", "Deleted temp directory: " + dirDeleted);
                                        }


                                    }




                                    else {
                                        String lastError = Config.getLastCommandOutput();
                                        Log.e("FFmpegDebug", "Error details: " + lastError);
                                        mainHandler.post(() -> {
                                            MainActivity.textView.setText("Conversion failed! Error: " + lastError);
                                            Toast.makeText(context, "Conversion failed!", Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                }
                            });

                        } catch (Exception e) {
                            Log.e("ConversionDebug", "Error getting file path: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }).start();

                return Uri.fromFile(outputFile);
            } catch (Exception e) {
                Log.e("ConversionDebug", "Error during conversion: " + e.getMessage());
                e.printStackTrace();
            }
        }




        else if (sourceFormat.equals("webp") && destinationFormat.equals("mp4")) {
            try {

                Log.d("ConversionDebug", "Starting animated WebP to MP4 conversion");
                Log.d("Position enter check", String.valueOf(position));
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                MainActivity.textView.setText("Starting WebP to MP4 conversion");
                                adapter.updateProgress(position, 0);
                            }
                        });

                        try {
                            Log.d("Check Uri", String.valueOf(sourceUri));

                            //File framesDir = new File(context.getCacheDir(), "frames");
                            File framesDir = new File(context.getCacheDir(), "frames_" + System.currentTimeMillis());
                            Log.d("FramesDir", "Frames directory path: " + framesDir.getAbsolutePath());

                            if (!framesDir.exists()) {
                                framesDir.mkdirs();
                            }

                            ImageDecoder.Source source = ImageDecoder.createSource(context.getContentResolver(), sourceUri);
                            Drawable drawable = ImageDecoder.decodeDrawable(source);
                            //AnimatedImageDrawable drawable = (AnimatedImageDrawable) ImageDecoder.decodeDrawable(source);
                            // Correct method names
                            Pair<Integer, Integer> info = ImageConverter.getWebPInfo(context, sourceUri);

                            int durationgetWebp = info.first;
                            int frameCountgetWebp = info.second;
                            // Calculate FPS
                            float fps = (float) (frameCountgetWebp * 1000) / durationgetWebp;


                            Log.d("WebP", "Duration: " + durationgetWebp + "ms, Frames: " + frameCountgetWebp + "FPS--" + fps);

                            if (drawable instanceof AnimatedImageDrawable) {
                                AnimatedImageDrawable animatedWebP = (AnimatedImageDrawable) drawable;

                                Log.d("ConversionDebug", "Starting frame extraction. Frame count: " + frameCountgetWebp);
                                animatedWebP.start();

                                for (int i = 0; i < frameCountgetWebp; i++) {
                                    Log.d("ConversionDebug", "Processing frame " + i + " of " + frameCountgetWebp);
                                    int remainingTime = frameCountgetWebp - i - 1;
                                    final float extractionProgress = (i * 50f) / frameCountgetWebp; // First 50%
                                    if (i % 5 == 0) {
                                        Log.d("Check", "Processing frame: " + i);

                                        mainHandler.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                adapter.updateProgress(position, (int) extractionProgress);
                                                MainActivity.textView.setText("Remeining Second: " + remainingTime );
                                            }
                                        });
                                    }

                                    Bitmap frame = drawableToBitmap(drawable);
                                    File frameFile = new File(framesDir, String.format("frame_%04d.png", i));
                                    FileOutputStream fos = new FileOutputStream(frameFile);
                                    frame.compress(Bitmap.CompressFormat.PNG, 100, fos);
                                    fos.close();
                                    frame.recycle();
                                    Log.d("ConversionDebug", "Frame " + i + " saved successfully");

                                    try {
                                        Thread.sleep(50);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }

                                Log.d("ConversionDebug", "Frame extraction complete. Starting FFmpeg conversion");

                                mainHandler.post(() -> {
                                    MainActivity.textView.setText("Combining frames into MP4...");
                                });

                                Log.d("ConversionDebug", "Starting FFmpeg setup");





                                // Log directory contents
                                File[] files = framesDir.listFiles();
                                Log.d("ConversionDebug", "Number of frames in directory: " + (files != null ? files.length : 0));
                                if (files != null) {
                                    for (File file : files) {
                                        Log.d("ConversionDebug", "Frame file: " + file.getName());
                                    }
                                }

                                Config.enableStatisticsCallback(new StatisticsCallback() {
                                    @Override
                                    public void apply(Statistics statistics) {
                                        // Get current frame
                                        int currentFrame = statistics.getVideoFrameNumber();
                                        // Get total frames (you need to calculate this based on duration and fps)
                                        int totalFrames = (int) (durationgetWebp * fps);

                                        // Calculate progress percentage
                                        // Calculate second 50% progress
                                        int ffmpegProgress = (currentFrame * 50) / totalFrames;
                                        int totalProgress = 50 + ffmpegProgress; // Add to first 50%

                                        mainHandler.post(() -> {
                                            adapter.updateProgress(position, totalProgress);
                                            MainActivity.textView.setText("Creating MP4: " + totalProgress + "%");
                                        });

                                        // Update UI for the current image being processed

                                    }
                                });


                                // Get values from settings
                                int quality = settings.getQuality(); // This is already inverted for CRF (0-51)
                                int resolution = settings.getResolution(); // 20-200%

                                // Calculate new dimensions based on resolution scaling
                                int newWidth = (drawable.getIntrinsicWidth() * resolution) / 100;
                                int newHeight = (drawable.getIntrinsicHeight() * resolution) / 100;


                                String[] cmd = {
                                        "-framerate", String.valueOf(fps),
                                        "-i", framesDir.getAbsolutePath() + "/frame_%04d.png",
                                        "-c:v", "mpeg4",
                                        "-q:v", String.valueOf(quality),
                                        "-vf", "scale=" + newWidth + ":" + newHeight,
                                        "-pix_fmt", "yuv420p",
                                        "-preset", "medium",
                                        "-t", String.valueOf(durationgetWebp),
                                        "-y",
                                        outputFile.getAbsolutePath()
                                };

                                Log.d("FFmpegDebug", "About to execute FFmpeg command");
                                Log.d("FFmpegDebug", "Command parameters: " + Arrays.toString(cmd));
                                Log.d("FFmpegDebug", "Input directory exists: " + framesDir.exists());
                                Log.d("FFmpegDebug", "Number of frame files: " + (framesDir.listFiles() != null ? framesDir.listFiles().length : 0));

                                FFmpeg.executeAsync(cmd, new ExecuteCallback() {
                                    @Override
                                    public void apply(long executionId, int returnCode) {
                                        Log.d("FFmpegDebug", "FFmpeg execution started with ID: " + executionId);
                                        Log.d("FFmpegDebug", "FFmpeg process return code: " + returnCode);
                                        Log.d("FFmpegDebug", "FFmpeg output: " + Config.getLastCommandOutput());



                                        if (returnCode == 0) {
                                            adapter.updateProgress(position, 100);
                                            adapter.setConversionComplete(position);
                                            TotalLoopCounter = TotalLoopCounter +1;
                                            if(TotalLoopCounter == TotalConversion) {
                                                mainHandler.post(() -> {
                                                    // Update progress for all items in the adapter
                                                    Log.d("Position check", String.valueOf(position));


                                                    Config.enableStatisticsCallback(null);
                                                    MainActivity.textView.setText("Conversion completed successfully!");
                                                    Toast.makeText(context, "Conversion completed!", Toast.LENGTH_SHORT).show();
                                                    ((Activity) context).findViewById(R.id.downloadButton).setEnabled(true);
                                                    ((Activity) context).findViewById(R.id.downloadButton).setAlpha(1.0f);
                                                    TotalLoopCounter =0;
                                                });

                                                // Check if directory exists and has files
                                                if (framesDir != null && framesDir.exists()) {
                                                    File[] files = framesDir.listFiles();
                                                    if (files != null) {
                                                        for (File file : files) {
                                                            boolean deleted = file.delete();
                                                            Log.d("FileDebug", "Deleted file: " + file.getName() + " - " + deleted);
                                                        }
                                                    }
                                                    // Delete the temp directory itself
                                                    boolean dirDeleted = framesDir.delete();
                                                    Log.d("FileDebug", "Deleted temp directory: " + dirDeleted);
                                                }


                                            }
                                        } else {
                                            String lastError = Config.getLastCommandOutput();
                                            Log.e("FFmpegDebug", "Error details: " + lastError);
                                            mainHandler.post(() -> {
                                                MainActivity.textView.setText("Conversion failed! Error: " + lastError);
                                                Toast.makeText(context, "Conversion failed!", Toast.LENGTH_SHORT).show();

                                            });
                                        }
                                    }
                                });

                                Log.d("FFmpegDebug", "FFmpeg command dispatched");
                            }
                            else{
                                int quality = settings.getQuality();
                                File tempInputFile = new File(context.getCacheDir(), "temp_input.webp");

                                // Copy WebP to temp file
                                InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
                                FileOutputStream outputStream = new FileOutputStream(tempInputFile);
                                byte[] buffer = new byte[1024];
                                int length;
                                while ((length = inputStream.read(buffer)) > 0) {
                                    outputStream.write(buffer, 0, length);
                                }
                                outputStream.close();
                                inputStream.close();

                                // FFmpeg command for static image to MP4
                                String[] cmd = {
                                        "-loop", "1",
                                        "-i", tempInputFile.getAbsolutePath(),
                                        "-t", "1",
                                        "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",
                                        "-pix_fmt", "yuv420p",
                                        "-crf", String.valueOf(quality),  // Add quality control
                                        "-preset", "medium",              // Encoding preset
                                        "-y",
                                        outputFile.getAbsolutePath()
                                };

                                FFmpeg.executeAsync(cmd, (executionId, returnCode) -> {

                                    if (returnCode == 0) {
                                        adapter.updateProgress(position, 100);
                                        adapter.setConversionComplete(position);
                                        TotalLoopCounter = TotalLoopCounter +1;
                                        if(TotalLoopCounter == TotalConversion) {
                                            mainHandler.post(() -> {
                                                // Update progress for all items in the adapter
                                                Log.d("Position check", String.valueOf(position));



                                                MainActivity.textView.setText("Conversion completed successfully!");
                                                Toast.makeText(context, "Conversion completed!", Toast.LENGTH_SHORT).show();
                                                ((Activity) context).findViewById(R.id.downloadButton).setEnabled(true);
                                                ((Activity) context).findViewById(R.id.downloadButton).setAlpha(1.0f);
                                                TotalLoopCounter =0;
                                            });

                                            // Check if directory exists and has files
                                            tempInputFile.delete();


                                        }
                                    } else {
                                        String lastError = Config.getLastCommandOutput();
                                        Log.e("FFmpegDebug", "Error details: " + lastError);
                                        mainHandler.post(() -> {
                                            MainActivity.textView.setText("Conversion failed! Error: " + lastError);
                                            Toast.makeText(context, "Conversion failed!", Toast.LENGTH_SHORT).show();

                                        });
                                    }
                                });
                            }
                        } catch (Exception e) {
                            Log.e("ConversionDebug", "Error during conversion: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }).start();

                return Uri.fromFile(outputFile);
            } catch (Exception e) {
                Log.e("ConversionDebug", "Error during conversion: " + e.getMessage());
                e.printStackTrace();
            }
        }



        else if (sourceFormat.equals("gif") && destinationFormat.equals("mp4")) {
            try {
                Log.d("ConversionDebug", "Starting GIF to MP4 conversion");

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mainHandler.post(() -> {
                            MainActivity.textView.setText("Starting GIF to MP4 conversion");
                            adapter.updateProgress(position, 0);
                        });

                        try {
                            // Create temp file for input GIF
                            File tempInputFile = new File(context.getCacheDir(), "temp_" + System.currentTimeMillis() + "input.gif");
                            InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
                            FileOutputStream outputStream = new FileOutputStream(tempInputFile);

                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);
                            }
                            outputStream.flush();
                            outputStream.close();
                            inputStream.close();
                            // Get values from settings
                            int quality = settings.getQuality(); // CRF value (0-51)
                            int resolution = settings.getResolution(); // 20-200%
                            // Get total frames from GIF
                            GifDrawable gifDrawable = new GifDrawable(tempInputFile.getAbsolutePath());

                            // Get GIF information
                            int durationMs = gifDrawable.getDuration();
                            int totalFrames = gifDrawable.getNumberOfFrames();

                            // Calculate frame rate if needed
                            float frameRate = (float) (totalFrames * 1000) / durationMs;

                            // Calculate new dimensions based on resolution scaling
                            int newWidth = (gifDrawable.getIntrinsicWidth() * resolution) / 100;
                            int newHeight = (gifDrawable.getIntrinsicHeight() * resolution) / 100;


                            // Use these values for your conversion
                            Log.d("GIF", "Duration: " + durationMs + "ms, Frames: " + totalFrames + ", FPS: " + frameRate);

                            gifDrawable.recycle();



                            // Enable statistics callback
                            Config.enableStatisticsCallback(new StatisticsCallback() {
                                @Override
                                public void apply(Statistics statistics) {
                                    int currentFrame = statistics.getVideoFrameNumber();
                                    if (currentFrame > 0) {
                                        int progress = (currentFrame * 100) / totalFrames;
                                        mainHandler.post(() -> {
                                            adapter.updateProgress(position, progress);
                                            MainActivity.textView.setText("Converting frame: " + currentFrame + "/" + totalFrames);
                                        });
                                    }
                                }
                            });




                            String[] cmd = {
                                    "-i", tempInputFile.getAbsolutePath(),
                                    "-movflags", "faststart",
                                    "-pix_fmt", "yuv420p",
                                    "-vf", "scale=" + newWidth + ":" + newHeight,
                                    "-preset", "medium",
                                    "-crf", String.valueOf(quality),
                                    "-r", String.valueOf(frameRate),  // Add frame rate
                                    "-y",
                                    outputFile.getAbsolutePath()
                            };


                            FFmpeg.executeAsync(cmd, new ExecuteCallback() {
                                @Override
                                public void apply(long executionId, int returnCode) {
                                    Config.enableStatisticsCallback(null);
                                    tempInputFile.delete();

                                    if (returnCode == 0) {
                                        TotalLoopCounter = TotalLoopCounter + 1;

                                        if (TotalLoopCounter < TotalConversion) {
                                            Log.d("Position else if check", String.valueOf(position));
                                            adapter.updateProgress(position, 100);
                                            adapter.setConversionComplete(position);
                                        }
                                        else if (TotalLoopCounter == TotalConversion) {
                                            mainHandler.post(() -> {
                                                adapter.updateProgress(position, 100);
                                                adapter.setConversionComplete(position);
                                                MainActivity.textView.setText("Conversion completed successfully!");
                                                Toast.makeText(context, "Conversion completed!", Toast.LENGTH_SHORT).show();
                                                ((Activity) context).findViewById(R.id.downloadButton).setEnabled(true);
                                                ((Activity) context).findViewById(R.id.downloadButton).setAlpha(1.0f);
                                                TotalLoopCounter = 0;
                                            });
                                        }

                                        if (tempInputFile != null && tempInputFile.exists()) {
                                            File[] files = tempInputFile.listFiles();
                                            if (files != null) {
                                                for (File file : files) {
                                                    boolean deleted = file.delete();
                                                    Log.d("FileDebug", "Deleted file: " + file.getName() + " - " + deleted);
                                                }
                                            }
                                            // Delete the temp directory itself
                                            boolean dirDeleted = tempInputFile.delete();
                                            Log.d("FileDebug", "Deleted temp directory: " + dirDeleted);
                                        }
                                    } else {
                                        String lastError = Config.getLastCommandOutput();
                                        Log.e("FFmpegDebug", "Error details: " + lastError);
                                        mainHandler.post(() -> {
                                            MainActivity.textView.setText("Conversion failed! Error: " + lastError);
                                            Toast.makeText(context, "Conversion failed!", Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                }
                            });

                        } catch (Exception e) {
                            Log.e("ConversionDebug", "Error during conversion: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }).start();

                return Uri.fromFile(outputFile);
            } catch (Exception e) {
                Log.e("ConversionDebug", "Error during conversion: " + e.getMessage());
                e.printStackTrace();
            }
        }


        else if (sourceFormat.equals("mp4") && destinationFormat.equals("gif")) {
            try {
                Log.d("ConversionDebug", "Starting MP4 to GIF conversion");

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mainHandler.post(() -> {
                            MainActivity.textView.setText("Starting MP4 to GIF conversion");
                            adapter.updateProgress(position, 0);
                        });

                        try {
                            // Create temp file for input MP4
                            File tempInputFile = new File(context.getCacheDir(), "temp_input_" + System.currentTimeMillis() + ".mp4");
                            InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
                            FileOutputStream outputStream = new FileOutputStream(tempInputFile);


                            // Get video duration for progress calculation
                            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                            retriever.setDataSource(context, sourceUri);
                            String frameRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
                            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                            int originalFps = (frameRateStr != null) ? Math.round(Float.parseFloat(frameRateStr)) : 25;
                            long durationMs = Long.parseLong(durationStr);
                            int totalFrames = (int) ((durationMs / 1000.0) * originalFps);
                            retriever.release();

                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);
                            }

                            inputStream.close();
                            outputStream.close();


                            // Then use it in the FFmpeg command

                            // Enable statistics callback
                            Config.enableStatisticsCallback(new StatisticsCallback() {
                                @Override
                                public void apply(Statistics statistics) {
                                    int currentFrame = statistics.getVideoFrameNumber();
                                    if (currentFrame > 0) {
                                        int progress = (currentFrame * 100) / totalFrames;
                                        mainHandler.post(() -> {
                                            adapter.updateProgress(position, progress);
                                        });
                                    }
                                }
                            });


                            // Get quality value from settings
                            int quality = settings.getQuality(); // 0-100

                            // Create FFmpeg command with quality settings
                            String[] cmd = {
                                    "-i", tempInputFile.getAbsolutePath(),
                                    "-vf", "fps=" + originalFps + ",scale=320:-1:flags=lanczos,split[s0][s1];[s0]palettegen=max_colors=" + quality + "[p];[s1][p]paletteuse=dither=bayer",
                                    "-y",
                                    outputFile.getAbsolutePath()
                            };


                            FFmpeg.executeAsync(cmd, new ExecuteCallback() {
                                @Override
                                public void apply(long executionId, int returnCode) {

                                    Config.enableStatisticsCallback(null);
                                    if (returnCode == 0) {
                                        TotalLoopCounter = TotalLoopCounter + 1;

                                        if (TotalLoopCounter < TotalConversion) {
                                            Log.d("Position else if check", String.valueOf(TotalLoopCounter - 1));
                                            adapter.updateProgress(position, 100);
                                            adapter.setConversionComplete(position);
                                        }
                                        else if (TotalLoopCounter == TotalConversion) {
                                            mainHandler.post(() -> {
                                                adapter.updateProgress(position, 100);
                                                adapter.setConversionComplete(position);
                                                MainActivity.textView.setText("Conversion completed successfully!");
                                                Toast.makeText(context, "Conversion completed!", Toast.LENGTH_SHORT).show();
                                                ((Activity) context).findViewById(R.id.downloadButton).setEnabled(true);
                                                ((Activity) context).findViewById(R.id.downloadButton).setAlpha(1.0f);
                                                TotalLoopCounter = 0;
                                            });
                                        }
                                        if (tempInputFile.exists()) {
                                            tempInputFile.delete();
                                        }

                                    }
                                    else {
                                        String lastError = Config.getLastCommandOutput();
                                        Log.e("FFmpegDebug", "Error details: " + lastError);
                                        mainHandler.post(() -> {
                                            MainActivity.textView.setText("Conversion failed! Error: " + lastError);
                                            Toast.makeText(context, "Conversion failed!", Toast.LENGTH_SHORT).show();
                                        });
                                    }
                                }
                            });

                        } catch (Exception e) {
                            Log.e("ConversionDebug", "Error during conversion: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }).start();

                return Uri.fromFile(outputFile);
            } catch (Exception e) {
                Log.e("ConversionDebug", "Error during conversion: " + e.getMessage());
                e.printStackTrace();
            }
        }


        else if (sourceFormat.equals("mp4") && destinationFormat.equals("webp")) {
            try {
                Log.d("ConversionDebug", "Starting MP4 to WebP conversion");

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mainHandler.post(() -> {
                            MainActivity.textView.setText("Starting MP4 to WebP conversion");
                            adapter.updateProgress(position, 0);
                        });

                        try {
                            // Get original frame rate and dimensions
                            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                            retriever.setDataSource(context, sourceUri);
                            String frameRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
                            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                            int width = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                            int height = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                            int originalFps = (frameRateStr != null) ? Math.round(Float.parseFloat(frameRateStr)) : 25;
                            long durationMs = Long.parseLong(durationStr);
                            int totalFrames = (int) ((durationMs / 1000.0) * originalFps);
                            retriever.release();

                            // Create temp directories

                            File framesDir = new File(context.getCacheDir(), "framesmp4Webp" + System.currentTimeMillis());
                            if (!framesDir.exists()) {
                                framesDir.mkdirs();
                            }

                            // Create temp file for input MP4
                            File tempInputFile = new File(context.getCacheDir(), "temp_input_" + System.currentTimeMillis() + ".mp4");
                            InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
                            FileOutputStream outputStream = new FileOutputStream(tempInputFile);

                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);
                            }

                            inputStream.close();
                            outputStream.close();

                            Log.d("ConversionDebug", "Starting conversion for file: " + sourceUri);
                            Log.d("ConversionDebug", "Total files to convert: " + TotalConversion);

                            Log.d("FileDebug", "Frames directory created: " + framesDir.getAbsolutePath());
                            Log.d("FileDebug", "Temp input file created: " + tempInputFile.getAbsolutePath());

                            Log.d("ConversionDebug", "Starting frame extraction");
                            mainHandler.post(() -> {
                                MainActivity.textView.setText("Extracting frames from MP4...");
                            });

                            String[] extractCmd = {
                                    "-i", tempInputFile.getAbsolutePath(),
                                    "-vf", "fps=" + originalFps,
                                    framesDir.getAbsolutePath() + "/frame_%04d.png"
                            };

                            Config.enableStatisticsCallback(new StatisticsCallback() {
                                @Override
                                public void apply(Statistics statistics) {
                                    int currentFrame = statistics.getVideoFrameNumber();
                                    if (currentFrame > 0) {
                                        int progress = (currentFrame * 50) / totalFrames; // First 50% of progress
                                        mainHandler.post(() -> {
                                            adapter.updateProgress(position, progress);
                                            MainActivity.textView.setText("Extracting frames: " + currentFrame + "/" + totalFrames);
                                        });
                                    }
                                }
                            });

                            FFmpeg.executeAsync(extractCmd, new ExecuteCallback() {
                                @Override
                                public void apply(long executionId, int returnCode) {
                                    if (returnCode == 0) {
                                        Log.d("ConversionDebug", "Frame extraction completed. Starting WebP creation");
                                        mainHandler.post(() -> {
                                            MainActivity.textView.setText("Creating WebP animation...");
                                        });

                                        // Get quality settings
                                        int quality = settings.getQuality();
                                        int compression = settings.getCompression();
                                        int resolution = settings.getResolution();

                                        // Calculate new dimensions
                                        int newWidth = (width * resolution) / 100;
                                        int newHeight = (height * resolution) / 100;

                                        String[] webpCmd = {
                                                "-framerate", String.valueOf(originalFps),
                                                "-i", framesDir.getAbsolutePath() + "/frame_%04d.png",
                                                "-vf", "scale=" + newWidth + ":" + newHeight,
                                                "-vcodec", "libwebp",
                                                "-lossless", "0",
                                                "-compression_level", String.valueOf(compression),
                                                "-quality", String.valueOf(quality),
                                                "-loop", "0",
                                                "-preset", "picture",
                                                "-an",
                                                "-vsync", "0",
                                                "-y",
                                                outputFile.getAbsolutePath()
                                        };

                                        Config.enableStatisticsCallback(new StatisticsCallback() {
                                            @Override
                                            public void apply(Statistics statistics) {
                                                int currentFrame = statistics.getVideoFrameNumber();
                                                if (currentFrame > 0) {
                                                    int progress = 50 + (currentFrame * 50) / totalFrames; // Last 50% of progress
                                                    mainHandler.post(() -> {
                                                        adapter.updateProgress(position, progress);
                                                        MainActivity.textView.setText("Creating WebP: " + currentFrame + "/" + totalFrames);
                                                    });
                                                }
                                            }
                                        });

                                        FFmpeg.executeAsync(webpCmd, new ExecuteCallback() {
                                            @Override
                                            public void apply(long executionId, int returnCode) {


                                                Config.enableStatisticsCallback(null);
                                                if (returnCode == 0) {

//                                                    File[] frameFiles = framesDir.listFiles();
//                                                    if (frameFiles != null) {
//                                                        for (File file : frameFiles) {
//                                                            file.delete();
//                                                        }
//                                                    }
//                                                    framesDir.delete();
//                                                    tempInputFile.delete();
                                                    TotalLoopCounter = TotalLoopCounter + 1;
                                                    if (TotalLoopCounter < TotalConversion) {
                                                        adapter.updateProgress(position, 100);
                                                        adapter.setConversionComplete(position);
                                                    }
                                                    else if (TotalLoopCounter == TotalConversion) {
                                                        mainHandler.post(() -> {
                                                            adapter.updateProgress(position, 100);
                                                            adapter.setConversionComplete(position);
                                                            MainActivity.textView.setText("Conversion completed successfully!");
                                                            Toast.makeText(context, "Conversion completed!", Toast.LENGTH_SHORT).show();
                                                            ((Activity) context).findViewById(R.id.downloadButton).setEnabled(true);
                                                            ((Activity) context).findViewById(R.id.downloadButton).setAlpha(1.0f);
                                                            TotalLoopCounter = 0;
                                                        });
                                                        // Clean up frames directory
                                                        File[] frameFiles = framesDir.listFiles();
                                                        if (frameFiles != null) {
                                                            for (File file : frameFiles) {
                                                                file.delete();
                                                            }
                                                        }
                                                        framesDir.delete();

                                                        // Delete temp input file (single file)
                                                        tempInputFile.delete();
                                                    }
                                                } else {
                                                    String lastError = Config.getLastCommandOutput();
                                                    Log.e("FFmpegDebug", "Error details: " + lastError);
                                                    mainHandler.post(() -> {
                                                        MainActivity.textView.setText("Conversion failed! Error: " + lastError);
                                                        Toast.makeText(context, "Conversion failed!", Toast.LENGTH_SHORT).show();
                                                    });
                                                }
                                            }
                                        });
                                    }
                                }
                            });
                        } catch (Exception e) {
                            Log.e("ConversionDebug", "Error during conversion: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }).start();

                return Uri.fromFile(outputFile);
            } catch (Exception e) {
                Log.e("ConversionDebug", "Error during conversion: " + e.getMessage());
                e.printStackTrace();
            }
        }







        inputStream.close();
        return Uri.fromFile(outputFile);

    }

    private static boolean isAnimatedFormat(String format) {
        format = format.toLowerCase();
        return format.equals("gif") || format.equals("webp") || format.equals("mp4");
    }



    // Add this helper method
    private static Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }




    // Add this method to get WebP frame rate



















    //---------------------------------------------------------------------------------


//-----------------------------------------------------------------------------------












    //--------------------------------------------------------------










//-----------------------------------------------------





    public static Pair<Integer, Integer> getWebPInfo(Context context, Uri sourceUri){

        try {
            InputStream inputStreamget = context.getContentResolver().openInputStream(sourceUri);
            byte[] bytes = IOUtils.toByteArray(inputStreamget);

            ImageDecodeOptions decodeOptions = ImageDecodeOptions.defaults();
            WebPImage webPImage = WebPImage.createFromByteArray(bytes, decodeOptions);

            int durationMs = webPImage.getDuration();
            int frameCount = webPImage.getFrameCount();

            Log.d("getWebPInfo", "Duration--" + durationMs + "  FrameCount--" + frameCount);

            inputStreamget.close();
            return new Pair<>(durationMs, frameCount);

        } catch (IOException e) {
            e.printStackTrace();
            return new Pair<>(0, 0);
        }
    }






}
