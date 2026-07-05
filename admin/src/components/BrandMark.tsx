/**
 * Иконочная марка «Метро Душанбе» (арка тоннеля + красная M + рельсы),
 * вырезана из photo/dushanbe_metro_logo.svg. Navy-части рисуются через
 * currentColor: на тёмном сайдбаре марка автоматически становится белой,
 * красная M остаётся брендовой. Скопирована из web/src/components/BrandMark.tsx
 * (admin — отдельное приложение, дублирование допустимо).
 */

type BrandMarkProps = {
  className?: string;
  /**
   * Цвет «просвета» центрального рельса: на белом фоне — белый,
   * на navy-фоне — var(--brand-navy), чтобы сохранить рисунок пути.
   */
  holeColor?: string;
};

export default function BrandMark({
  className,
  holeColor = "#FFFFFF",
}: BrandMarkProps) {
  return (
    <svg
      viewBox="230 80 740 675"
      className={className}
      aria-hidden="true"
      focusable="false"
    >
      <g transform="translate(0,10)">
        {/* арка тоннеля */}
        <path
          fill="currentColor"
          fillRule="evenodd"
          d="M260 555 L260 345 C260 169 404 70 600 70 C796 70 940 169 940 345 L940 555 L875 555 L875 345 C875 210 766 135 600 135 C434 135 325 210 325 345 L325 555 Z"
        />
        {/* красная M */}
        <path
          fill="#E21B2D"
          d="M418 505 L448 250 L600 405 L752 250 L782 505 L690 505 L675 355 L600 435 L525 355 L510 505 Z"
        />
        {/* рельсы в перспективе */}
        <path fill="currentColor" d="M460 565 L560 565 L390 745 L230 745 Z" />
        <path fill="currentColor" d="M640 565 L740 565 L970 745 L810 745 Z" />
        <path
          fill="currentColor"
          d="M545 565 L655 565 L730 745 L470 745 Z"
          opacity="0.98"
        />
        <path fill={holeColor} d="M570 565 L630 565 L675 745 L525 745 Z" />
        {/* шпалы */}
        <g fill="currentColor">
          <rect x="550" y="590" width="100" height="10" rx="2" />
          <rect x="535" y="620" width="130" height="12" rx="2" />
          <rect x="515" y="655" width="170" height="14" rx="2" />
          <rect x="490" y="700" width="220" height="17" rx="2" />
        </g>
      </g>
    </svg>
  );
}
