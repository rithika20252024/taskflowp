import type { Metadata } from 'next';
import './globals.css';
export const metadata: Metadata = { title: 'TaskFlow Pro', description: 'A clear home for your team work.' };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) { return <html lang="en"><body>{children}</body></html>; }
