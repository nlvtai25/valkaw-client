import { asset } from '@/lib/asset';

interface LogoProps {
  showText?: boolean;
}

/** Wordmark: the golden V mark plus the name in the display face. */
export function Logo({ showText = true }: LogoProps) {
  return (
    <>
      <img src={asset('/valkaw-logo.png')} alt="" width={512} height={512} aria-hidden="true" />
      {showText && (
        <span>
          Valkaw<em>Client</em>
        </span>
      )}
    </>
  );
}

export default Logo;
