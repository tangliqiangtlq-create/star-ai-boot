'use client';

import { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';

interface VideoPlayerProps {
  src: string;
  poster?: string;
  previewSec?: number;
  isPaid?: boolean;
  autoPlay?: boolean;
  className?: string;
}

export function VideoPlayer({
  src,
  poster,
  previewSec,
  isPaid,
  autoPlay,
  className
}: VideoPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const [showPreviewOverlay, setShowPreviewOverlay] = useState(false);
  const [isPreviewing, setIsPreviewing] = useState(false);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !src) return;

    if (src.includes('.m3u8') && Hls.isSupported()) {
      const hls = new Hls({ enableWorker: true, lowLatencyMode: false });
      hls.loadSource(src);
      hls.attachMedia(video);
      hlsRef.current = hls;
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = src;
    } else {
      video.src = src;
    }

    return () => {
      hlsRef.current?.destroy();
    };
  }, [src]);

  useEffect(() => {
    const video = videoRef.current;
    if (!video || !isPaid || !previewSec) return;

    const handleTimeUpdate = () => {
      if (video.currentTime >= previewSec && !isPreviewing) {
        video.pause();
        setShowPreviewOverlay(true);
      }
    };

    video.addEventListener('timeupdate', handleTimeUpdate);
    return () => video.removeEventListener('timeupdate', handleTimeUpdate);
  }, [isPaid, previewSec, isPreviewing]);

  const handlePreview = () => {
    const video = videoRef.current;
    if (!video) return;
    setShowPreviewOverlay(false);
    setIsPreviewing(true);
    video.currentTime = 0;
    video.play();
  };

  return (
    <div className={`relative w-full bg-black ${className ?? ''}`}>
      <video
        ref={videoRef}
        poster={poster}
        controls
        playsInline
        autoPlay={autoPlay}
        className="w-full h-full object-contain"
      />

      {showPreviewOverlay && isPaid && (
        <div className="absolute inset-0 bg-black/80 flex flex-col items-center justify-center p-4">
          <div className="text-white text-center">
            <p className="text-lg font-bold mb-2">试看已结束</p>
            <p className="text-sm mb-4 opacity-80">开通会员或购买解锁完整观看</p>
            <div className="flex gap-3 justify-center">
              <button onClick={handlePreview} className="px-4 py-2 bg-white/20 rounded-lg text-white">
                再试看 {previewSec}s
              </button>
              <button className="px-4 py-2 bg-brand-600 rounded-lg text-white font-bold">
                立即解锁
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
