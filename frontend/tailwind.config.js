/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        ai: {
          primary: '#4f46e5',
          secondary: '#06b6d4',
          dark: '#0f172a',
          card: '#1e293b'
        }
      }
    },
  },
  plugins: [],
  corePlugins: {
    preflight: false, // 防止与 Ant Design 基础样式产生冲突
  }
}
