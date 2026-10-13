import ReactMarkdown from 'react-markdown';

export function Markdown({ children }: { children: string }) {
  return <ReactMarkdown skipHtml>{children}</ReactMarkdown>;
}
