import React, { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark, oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { CheckOutlined, CopyOutlined } from '@ant-design/icons';
import { message } from 'antd';
import { useThemeStore } from '../store/useThemeStore';

interface MarkdownRendererProps {
  content: string;
  className?: string;
}

const CodeBlock: React.FC<{
  inline?: boolean;
  className?: string;
  children?: React.ReactNode;
}> = ({ inline, className, children, ...props }) => {
  const [copied, setCopied] = useState(false);
  const { isDark } = useThemeStore();
  const match = /language-(\w+)/.exec(className || '');
  const language = match ? match[1] : '';
  const codeString = String(children).replace(/\n$/, '');

  const handleCopy = () => {
    navigator.clipboard.writeText(codeString);
    setCopied(true);
    message.success('代码已复制到剪贴板');
    setTimeout(() => setCopied(false), 2000);
  };

  if (!inline && (match || codeString.includes('\n'))) {
    return (
      <div className="relative my-3 rounded-xl overflow-hidden border border-slate-200/80 dark:border-slate-800 shadow-sm group">
        <div className="flex items-center justify-between px-3.5 py-1.5 bg-slate-100 dark:bg-slate-800/90 text-xs text-slate-500 dark:text-slate-400 font-mono select-none border-b border-slate-200/60 dark:border-slate-800">
          <span className="font-semibold uppercase tracking-wide">{language || 'code'}</span>
          <button
            onClick={handleCopy}
            className="flex items-center gap-1 text-[11px] hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
          >
            {copied ? (
              <>
                <CheckOutlined className="text-emerald-500" />
                <span className="text-emerald-500">已复制</span>
              </>
            ) : (
              <>
                <CopyOutlined />
                <span>复制代码</span>
              </>
            )}
          </button>
        </div>
        <SyntaxHighlighter
          style={isDark ? (oneDark as any) : (oneLight as any)}
          language={language || 'text'}
          PreTag="div"
          customStyle={{
            margin: 0,
            padding: '12px 16px',
            fontSize: '13px',
            lineHeight: 1.6,
            background: isDark ? '#141824' : '#fafafa',
          }}
          {...props}
        >
          {codeString}
        </SyntaxHighlighter>
      </div>
    );
  }

  return (
    <code
      className="px-1.5 py-0.5 mx-0.5 rounded-md bg-slate-100 dark:bg-slate-800 text-indigo-600 dark:text-indigo-400 text-xs font-mono font-medium border border-slate-200/60 dark:border-slate-700/60"
      {...props}
    >
      {children}
    </code>
  );
};

const isEmptyChildren = (children: any): boolean => {
  if (!children) return true;
  if (typeof children === 'string') return !children.trim();
  if (Array.isArray(children)) {
    return children.length === 0 || children.every(c => isEmptyChildren(c));
  }
  return false;
};

/**
 * 前端 Markdown 预处理器：
 * 1. 自动将连写漏换行的表格 "||" 或 "| |" 拆分成标准的每行换行 Markdown 表格
 * 2. 标题语法规范化
 * 3. 自动将残留的 HTML 表格 <table>...</table> 转换为标准 GFM Markdown 表格
 */
const preprocessMarkdown = (content: string): string => {
  if (!content) return '';

  let text = content
    .replace(/<br\s*\/?>/gi, '\n')
    .replace(/&quot;/g, '"')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&nbsp;/g, ' ')
    .trimStart();

  // 1. 标题语法规范化：支持大模型漏打空格的情况，例如 `###1.` 自动转为 `### 1.`
  text = text.replace(/(^|\n)(#{1,6})([^\s#\n])/g, '$1$2 $3');

  // 2. 修复未闭合管道符异常换行的表头，例如 "| 评估维度\n| 传统单体" -> "| 评估维度 | 传统单体"
  text = text.replace(/\|\s*([^|\r\n]+)\s*[\r\n]+\s*\|/g, '| $1 |');

  // 3. 修复大模型输出表格时连写漏掉换行符，将连写的 "||" 或 "| |" 拆分成紧凑的单换行 "\n|"
  text = text.replace(/\|\s*\|/g, '|\n|');

  // 4. 清理对齐分隔行粘连，例如 ":---||" 拆分为 ":---|\n|"
  text = text.replace(/(:---+)\s*\|/g, '$1|');

  // 5. 确保表格内部各行紧挨（消除多余空行破坏 GFM 表格语法）
  text = text.replace(/(\|[^\n]+\|)\s*\n\s*\n\s*(\|)/g, '$1\n$2');
  text = text.replace(/(\|[^\n]+\|)\s*\n\s*\n\s*(\|)/g, '$1\n$2');

  // 6. 表格前置换行补偿：如果表格首行紧贴文字未换行，补充独立段落空行
  text = text.replace(/([^\n])\n(\|(?:\s*[^|\r\n]+\s*\|)+[\r\n]+\|(?:\s*:?---+:?\s*\|)+)/g, '$1\n\n$2');

  // 5. 匹配 HTML 表格并转换为标准 Markdown 表格
  const tableRegex = /<table[^>]*>([\s\S]*?)<\/table>/gi;
  text = text.replace(tableRegex, (_, tableInner) => {
    const rowRegex = /<tr[^>]*>([\s\S]*?)<\/tr>/gi;
    const cellRegex = /<(?:td|th)[^>]*>([\s\S]*?)<\/(?:td|th)>/gi;
    const rows: string[][] = [];
    let maxCols = 0;

    let rowMatch;
    while ((rowMatch = rowRegex.exec(tableInner)) !== null) {
      const cells: string[] = [];
      let cellMatch;
      while ((cellMatch = cellRegex.exec(rowMatch[1])) !== null) {
        const cellText = cellMatch[1].replace(/<[^>]+>/g, '').replace(/[\r\n]+/g, ' ').trim();
        cells.push(cellText || '-');
      }
      if (cells.length > 0) {
        rows.push(cells);
        maxCols = Math.max(maxCols, cells.length);
      }
    }

    if (rows.length === 0) return '';

    const lines: string[] = [];
    const header = rows[0];
    const headerLine = '| ' + Array.from({ length: maxCols }, (_, i) => header[i] || '-').join(' | ') + ' |';
    const splitLine = '| ' + Array.from({ length: maxCols }, () => ':---').join(' | ') + ' |';
    lines.push(headerLine, splitLine);

    for (let r = 1; r < rows.length; r++) {
      const row = rows[r];
      lines.push('| ' + Array.from({ length: maxCols }, (_, i) => row[i] || '-').join(' | ') + ' |');
    }

    return '\n\n' + lines.join('\n') + '\n\n';
  });

  return text.replace(/<[/]?(?:p|div|span|tbody|thead|tfoot)[^>]*>/gi, '');
};

export const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({ content, className = '' }) => {
  const processedContent = preprocessMarkdown(content);

  return (
    <div className={`markdown-body text-[14.5px] leading-relaxed break-words text-slate-800 dark:text-slate-200 font-normal ${className}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          code: CodeBlock as any,
          table: ({ children }) => (
            <div className="overflow-x-auto my-5 rounded-xl border border-slate-200 dark:border-slate-800 shadow-xs bg-white dark:bg-slate-900">
              <table className="w-full border-collapse text-xs text-left">
                {children}
              </table>
            </div>
          ),
          thead: ({ children }) => (
            <thead className="bg-slate-100/80 dark:bg-slate-800 border-b border-slate-200 dark:border-slate-700 select-none">
              {children}
            </thead>
          ),
          tbody: ({ children }) => (
            <tbody className="divide-y divide-slate-100 dark:divide-slate-800">
              {children}
            </tbody>
          ),
          tr: ({ children }) => (
            <tr className="hover:bg-indigo-50/40 dark:hover:bg-indigo-950/20 transition-colors even:bg-slate-50/40 dark:even:bg-slate-850/40">
              {children}
            </tr>
          ),
          th: ({ children }) => (
            <th className="px-4 py-3 font-bold text-slate-900 dark:text-white tracking-wide text-xs bg-slate-100/70 dark:bg-slate-800/80">
              {children}
            </th>
          ),
          td: ({ children }) => (
            <td className="px-4 py-2.5 text-slate-700 dark:text-slate-300 leading-relaxed">
              {children}
            </td>
          ),
          a: ({ href, children }) => (
            <a
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              className="text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 underline font-semibold transition-colors"
            >
              {children}
            </a>
          ),
          ul: ({ children }) => (
            <ul className="list-disc pl-5 my-3 space-y-1.5 text-slate-800 dark:text-slate-200 marker:text-indigo-500">
              {children}
            </ul>
          ),
          ol: ({ children }) => (
            <ol className="list-decimal pl-5 my-3 space-y-1.5 text-slate-800 dark:text-slate-200 marker:text-indigo-500 font-medium">
              {children}
            </ol>
          ),
          li: ({ children }) => <li className="leading-relaxed pl-0.5">{children}</li>,
          h1: ({ children }) => {
            if (isEmptyChildren(children)) return null;
            return (
              <h1 className="text-xl font-bold first:mt-0 mt-7 mb-3 text-slate-950 dark:text-white border-b border-slate-200/90 dark:border-slate-800 pb-2.5 tracking-tight">
                {children}
              </h1>
            );
          },
          h2: ({ children }) => {
            if (isEmptyChildren(children)) return null;
            return (
              <h2 className="text-base font-bold first:mt-0 mt-6 mb-2.5 text-slate-900 dark:text-white tracking-tight flex items-center gap-2">
                <span>{children}</span>
              </h2>
            );
          },
          h3: ({ children }) => {
            if (isEmptyChildren(children)) return null;
            return (
              <h3 className="text-sm font-bold first:mt-0 mt-4 mb-2 text-slate-900 dark:text-white">
                {children}
              </h3>
            );
          },
          p: ({ children }) => {
            if (isEmptyChildren(children)) return null;
            return <p className="first:mt-0 mb-3 text-slate-800 dark:text-slate-200 leading-7 font-normal">{children}</p>;
          },
          strong: ({ children }) => <strong className="font-bold text-slate-950 dark:text-white">{children}</strong>,
          blockquote: ({ children }) => (
            <blockquote className="border-l-4 border-indigo-500 pl-4 py-2.5 my-3.5 bg-gradient-to-r from-indigo-50/50 to-transparent dark:from-indigo-950/20 dark:to-transparent rounded-r-xl text-slate-700 dark:text-slate-300 text-xs leading-relaxed font-mono">
              {children}
            </blockquote>
          ),
          hr: () => <hr className="my-5 border-slate-200 dark:border-slate-800" />,
        }}
      >
        {processedContent}
      </ReactMarkdown>
    </div>
  );
};
