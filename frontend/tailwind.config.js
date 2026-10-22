/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#eef4ff',
          100: '#dbe7ff',
          500: '#2f6bff',
          600: '#1f56e0',
          700: '#1a45b8',
          900: '#0f2a6b'
        },
        sidebar: {
          bg: '#0b1220',
          line: 'rgba(255,255,255,0.08)',
          text: 'rgba(255,255,255,0.72)',
          active: '#2f6bff'
        }
      },
      boxShadow: {
        side: '2px 0 12px rgba(15,23,42,0.08)'
      }
    }
  },
  plugins: []
}
