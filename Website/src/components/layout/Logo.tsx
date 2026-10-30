import { asset } from '@/lib/asset';

interface LogoProps {
  showText?: boolean;
}

/** Wordmark: the bird mark (the image carries it) plus the name in the display face. */
export function Logo({ showText = true }: LogoProps) {
  return (
    <>
      <img src={asset('/valkaw-logo.png')} alt="" width={512} height={272} aria-hidden="true" />
      {showText && (
        <span>
          Valkaw<em>Client</em>
        </span>
      )}
    </>
  );
}

export default Logo;
