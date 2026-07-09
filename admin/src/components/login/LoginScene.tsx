/**
 * Декоративная сцена страницы входа: ночной Душанбе в брендовых цветах —
 * хребты Памира, флаг РТ, тоннельная арка с уходящими рельсами (мотив
 * логотипа) и головной вагон с литерой «М». Чистый inline-SVG без внешних
 * ресурсов (офлайн-принцип, dev-conventions.md §8). Элемент декоративный:
 * aria-hidden, смысл несёт текст рядом.
 */

export default function LoginScene({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 640 420"
      className={className}
      aria-hidden="true"
      role="presentation"
    >
      <defs>
        <linearGradient id="lg-sky" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#0b1622" />
          <stop offset="0.62" stopColor="#082742" />
          <stop offset="1" stopColor="#0e3a5f" />
        </linearGradient>
        <linearGradient id="lg-glow" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#f2f5f8" stopOpacity="0.55" />
          <stop offset="1" stopColor="#f2f5f8" stopOpacity="0" />
        </linearGradient>
        <linearGradient id="lg-ridge-far" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#123a5e" />
          <stop offset="1" stopColor="#0d2c48" />
        </linearGradient>
        <linearGradient id="lg-ridge-near" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#0d2c48" />
          <stop offset="1" stopColor="#091f36" />
        </linearGradient>
      </defs>

      {/* Небо */}
      <rect width="640" height="420" fill="url(#lg-sky)" />

      {/* Звёзды */}
      <g fill="#f2f5f8" opacity="0.8">
        <circle cx="64" cy="52" r="1.4" />
        <circle cx="150" cy="30" r="1" />
        <circle cx="235" cy="70" r="1.2" />
        <circle cx="330" cy="38" r="1" />
        <circle cx="418" cy="64" r="1.4" />
        <circle cx="505" cy="34" r="1" />
        <circle cx="576" cy="78" r="1.2" />
        <circle cx="286" cy="102" r="0.9" opacity="0.6" />
        <circle cx="470" cy="110" r="0.9" opacity="0.6" />
      </g>

      {/* Дальний хребет со снежными вершинами */}
      <path
        d="M0 208 L58 156 L96 190 L150 132 L204 186 L258 148 L312 196 L368 140 L430 192 L488 150 L544 194 L594 162 L640 200 L640 420 L0 420 Z"
        fill="url(#lg-ridge-far)"
      />
      <g stroke="#e9eef4" strokeWidth="3" strokeLinecap="round" opacity="0.75" fill="none">
        <path d="M50 163 L58 156 L66 163" />
        <path d="M142 140 L150 132 L158 140" />
        <path d="M360 148 L368 140 L376 148" />
        <path d="M480 158 L488 150 L496 158" />
      </g>

      {/* Флаг Республики Таджикистан: полосы 2:3:2, корона с семью звёздами */}
      <g>
        <rect x="470" y="118" width="3" height="128" rx="1.5" fill="#d7dee6" />
        <g>
          <path d="M473 122 q26 -7 52 0 q26 7 52 0 L577 138.3 q-26 7 -52 0 q-26 -7 -52 0 Z" fill="#e21b2d" />
          <path d="M473 138.3 q26 -7 52 0 q26 7 52 0 L577 162.6 q-26 7 -52 0 q-26 -7 -52 0 Z" fill="#f2f5f8" />
          <path d="M473 162.6 q26 -7 52 0 q26 7 52 0 L577 179 q-26 7 -52 0 q-26 -7 -52 0 Z" fill="#138a3d" />
        </g>
        {/* Корона */}
        <g fill="#f8c300">
          <path d="M519 149.5 l2.2 -4.5 2 3 1.8 -4 1.8 4 2 -3 2.2 4.5 q-6 1.8 -12 0 Z" />
          <rect x="519.4" y="150.6" width="11.2" height="1.6" rx="0.8" />
          {/* Семь звёзд дугой над короной */}
          <circle cx="513.5" cy="147.5" r="0.9" />
          <circle cx="516" cy="144.6" r="0.9" />
          <circle cx="519.5" cy="142.6" r="0.9" />
          <circle cx="525" cy="141.9" r="0.9" />
          <circle cx="530.5" cy="142.6" r="0.9" />
          <circle cx="534" cy="144.6" r="0.9" />
          <circle cx="536.5" cy="147.5" r="0.9" />
        </g>
      </g>

      {/* Ближний хребет */}
      <path
        d="M0 262 L74 216 L136 252 L210 208 L286 258 L352 222 L420 262 L500 226 L570 260 L640 232 L640 420 L0 420 Z"
        fill="url(#lg-ridge-near)"
      />

      {/* Городская линия (силуэты зданий) */}
      <g fill="#0a2138">
        <rect x="24" y="266" width="26" height="46" rx="2" />
        <rect x="58" y="252" width="18" height="60" rx="2" />
        <rect x="88" y="272" width="30" height="40" rx="2" />
        <rect x="514" y="258" width="22" height="54" rx="2" />
        <rect x="546" y="272" width="30" height="40" rx="2" />
        <rect x="590" y="250" width="20" height="62" rx="2" />
      </g>
      <g fill="#f8d477" opacity="0.75">
        <rect x="30" y="274" width="4" height="4" rx="1" />
        <rect x="40" y="286" width="4" height="4" rx="1" />
        <rect x="63" y="262" width="4" height="4" rx="1" />
        <rect x="63" y="278" width="4" height="4" rx="1" />
        <rect x="96" y="280" width="4" height="4" rx="1" />
        <rect x="520" y="268" width="4" height="4" rx="1" />
        <rect x="528" y="284" width="4" height="4" rx="1" />
        <rect x="554" y="280" width="4" height="4" rx="1" />
        <rect x="596" y="260" width="4" height="4" rx="1" />
        <rect x="596" y="276" width="4" height="4" rx="1" />
      </g>

      {/* Тоннельная арка — мотив логотипа */}
      <path
        d="M188 420 L188 320 C188 240 248 196 320 196 C392 196 452 240 452 320 L452 420 L420 420 L420 322 C420 260 376 228 320 228 C264 228 220 260 220 322 L220 420 Z"
        fill="#0e3654"
      />
      <path
        d="M220 420 L220 322 C220 260 264 228 320 228 C376 228 420 260 420 322 L420 420 Z"
        fill="#071a2b"
      />

      {/* Рельсы в перспективе */}
      <g>
        <path d="M292 420 L306 300 L316 300 L308 420 Z" fill="#3d5a76" />
        <path d="M348 420 L334 300 L324 300 L332 420 Z" fill="#3d5a76" />
        <g fill="#2c4560">
          <rect x="300" y="316" width="40" height="5" rx="2" />
          <rect x="296" y="336" width="48" height="6" rx="2" />
          <rect x="291" y="360" width="58" height="7" rx="2" />
          <rect x="285" y="388" width="70" height="8" rx="2" />
        </g>
      </g>

      {/* Головной вагон */}
      <g>
        {/* Свет фар */}
        <ellipse cx="320" cy="404" rx="94" ry="26" fill="url(#lg-glow)" opacity="0.4" />
        {/* Кузов */}
        <path
          d="M264 420 L264 330 C264 306 286 292 320 292 C354 292 376 306 376 330 L376 420 Z"
          fill="#e9eef4"
        />
        {/* Лобовое стекло */}
        <path
          d="M276 336 C276 316 294 306 320 306 C346 306 364 316 364 336 L364 354 L276 354 Z"
          fill="#0b1622"
        />
        {/* Маска кабины */}
        <path d="M264 366 L376 366 L376 386 L264 386 Z" fill="#082742" />
        {/* Литера М */}
        <path
          d="M304 402 L308 376 L320 390 L332 376 L336 402 L327 402 L325.5 388 L320 395 L314.5 388 L313 402 Z"
          fill="#e21b2d"
        />
        {/* Фары */}
        <circle cx="285" cy="398" r="6" fill="#f8d477" />
        <circle cx="355" cy="398" r="6" fill="#f8d477" />
        {/* Красная строка маршрута */}
        <rect x="296" y="296" width="48" height="6" rx="3" fill="#e21b2d" opacity="0.9" />
      </g>
    </svg>
  );
}
