import Editor from '@monaco-editor/react';
import '../editor/monacoSetup';
import type { Language } from '../api/types';
import { MONACO_LANGUAGE } from '../editor/templates';

interface Props {
  language: Language;
  value: string;
  onChange: (value: string) => void;
}

export default function CodeEditor({ language, value, onChange }: Props) {
  return (
    <Editor
      height="100%"
      language={MONACO_LANGUAGE[language]}
      value={value}
      onChange={(next) => onChange(next ?? '')}
      options={{
        minimap: { enabled: false },
        fontSize: 14,
        tabSize: 4,
        scrollBeyondLastLine: false,
        automaticLayout: true,
        ariaLabel: 'Code editor',
      }}
    />
  );
}
